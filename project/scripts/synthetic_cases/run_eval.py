"""가상 품질 사례 흐름 검증: 적재 → 인덱싱 대기 → /similar-cases 평가.

평가용 앱(별도 DB·컬렉션)에 대고 실행한다. 다시 실행하면 out/id-map.json에 있는 사례·질의는 건너뛴다.
  python3 scripts/synthetic_cases/run_eval.py
환경변수: APP_URL(기본 http://localhost:8081), QDRANT_URL(http://localhost:6333),
         QDRANT_COLLECTION(quality_case_vectors_eval) — 앱에 준 컬렉션과 같아야 한다
"""
import json
import os
import time
from pathlib import Path

import requests

HERE = Path(__file__).parent
OUT = HERE / "out"
ID_MAP = OUT / "id-map.json"
APP = os.environ.get("APP_URL", "http://localhost:8081")
QDRANT = os.environ.get("QDRANT_URL", "http://localhost:6333")
COLLECTION = os.environ.get("QDRANT_COLLECTION", "quality_case_vectors_eval")
TIMEOUT = 60  # 첫 resolve는 토크나이저 로딩으로 수 초 걸린다
CASE_FIELDS = ["lotNo", "title", "problemDescription", "defectType",
               "productionQuantity", "defectQuantity", "occurredAt"]


def call(method, path, body=None):
    r = requests.request(method, APP + path, json=body, timeout=TIMEOUT)
    if not r.ok:
        raise RuntimeError(f"{method} {path} → {r.status_code} {r.text}")
    return r.json() if r.content else None


def save(id_map):
    OUT.mkdir(exist_ok=True)
    ID_MAP.write_text(json.dumps(id_map, ensure_ascii=False, indent=2))


def ensure_master(data):
    """코드로 기존 기준 정보를 찾고 없으면 만든다. {code: id} 반환."""
    ids = {}
    existing = {p["productCode"]: p["id"] for p in call("GET", "/api/products")}
    for p in data["products"]:
        ids[p["productCode"]] = existing.get(p["productCode"]) or call("POST", "/api/products", p)["id"]
    existing = {p["processCode"]: p["id"] for p in call("GET", "/api/processes")}
    for p in data["processes"]:
        ids[p["processCode"]] = existing.get(p["processCode"]) or call("POST", "/api/processes", p)["id"]
    for e in data["equipments"]:
        pid = ids[e["processCode"]]
        existing = {x["equipmentCode"]: x["id"] for x in call("GET", f"/api/processes/{pid}/equipments")}
        body = {"equipmentCode": e["equipmentCode"], "name": e["name"]}
        ids[e["equipmentCode"]] = existing.get(e["equipmentCode"]) or \
            call("POST", f"/api/processes/{pid}/equipments", body)["id"]
    return ids


def create_open(item, master):
    body = {k: item[k] for k in CASE_FIELDS}
    body |= {"productId": master[item["productCode"]], "processId": master[item["processCode"]],
             "equipmentId": master[item["equipmentCode"]]}
    return call("POST", "/api/quality-cases", body)["id"]


def load(data, queries, master, id_map):
    for c in data["cases"]:
        if c["genId"] in id_map:
            continue
        server_id = create_open(c, master)
        call("POST", f"/api/quality-cases/{server_id}/resolve",
             {k: c[k] for k in ["rootCause", "correctiveAction", "resultSummary"]})
        id_map[c["genId"]] = server_id
        save(id_map)  # 한 건마다 기록해야 중간 실패 후 이어서 실행할 수 있다
        print(f"적재 {c['genId']} → id {server_id}")
    for q in queries:
        if q["queryId"] in id_map:
            continue
        id_map[q["queryId"]] = create_open(q, master)  # 질의는 OPEN으로 두어 Qdrant에 들어가지 않는다
        save(id_map)
        print(f"질의 등록 {q['queryId']} → id {id_map[q['queryId']]}")


def wait_searchable(expected, limit_seconds=600):
    """qdrantStatus=COMPLETED 뒤에도 searchable 반영은 별도 job이라 Qdrant에서 직접 센다."""
    body = {"exact": True, "filter": {"must": [{"key": "searchable", "match": {"value": True}}]}}
    deadline = time.time() + limit_seconds
    while True:
        r = requests.post(f"{QDRANT}/collections/{COLLECTION}/points/count", json=body, timeout=10)
        count = r.json()["result"]["count"] if r.ok else 0
        print(f"검색 노출 {count}/{expected}")
        if count >= expected:
            return
        if time.time() > deadline:
            raise RuntimeError("인덱싱 대기 시간 초과")
        time.sleep(5)


def evaluate(data, queries, id_map):
    gen_of = {v: k for k, v in id_map.items()}
    role_of = {c["genId"]: c["role"] for c in data["cases"]}
    ks = [1, 3, 10]
    recalls, rrs, margins, inversions = {k: [] for k in ks}, [], [], 0
    for q in queries:
        results = call("GET", f"/api/quality-cases/{id_map[q['queryId']]}/similar-cases")
        ranked = [gen_of.get(r["qualityCase"]["id"], f"?{r['qualityCase']['id']}") for r in results]
        print(f"\n[{q['queryId']}] 정답 {q['relevant']} / 혼동 {q['confusing']}")
        for i, (gid, r) in enumerate(zip(ranked, results), 1):
            mark = "★정답" if gid in q["relevant"] else "✗혼동" if gid in q["confusing"] else ""
            print(f"  {i:2}. {gid:6} {role_of.get(gid, ''):9} {r['score']:.4f} {mark}")

        rel_ranks = [ranked.index(g) + 1 for g in q["relevant"] if g in ranked]
        best = min(rel_ranks) if rel_ranks else None
        for k in ks:
            recalls[k].append(sum(r <= k for r in rel_ranks) / len(q["relevant"]))
        rrs.append(1 / best if best else 0.0)
        # 혼동 역전: 혼동 사례가 정답보다 위에 있음 (정답이 결과에 없으면 혼동이 하나라도 있을 때 역전)
        conf_ranks = [ranked.index(g) + 1 for g in q["confusing"] if g in ranked]
        inverted = any(best is None or cr < best for cr in conf_ranks)
        inversions += inverted
        # 점수 차: 정답 최고점 - 혼동 최고점 (음수면 역전, 0에 가까우면 거의 구분 못 함). 둘 다 결과에 있을 때만
        score_of = {g: r["score"] for g, r in zip(ranked, results)}
        margin = None
        if best and conf_ranks:
            margin = score_of[ranked[best - 1]] - max(score_of[ranked[c - 1]] for c in conf_ranks)
            margins.append(margin)
        margin_text = f"{margin:+.4f}" if margin is not None else "-"
        print(f"  → 정답 순위 {best}, 혼동 역전 {'예' if inverted else '아니오'}, 정답-혼동 점수 차 {margin_text}")

    n = len(queries)
    recall_text = " | ".join(f"Recall@{k} {sum(recalls[k]) / n:.3f}" for k in ks)
    margin_avg = f"{sum(margins) / len(margins):+.4f}" if margins else "-"
    print(f"\n{recall_text} | MRR {sum(rrs) / n:.3f} | 혼동 역전 {inversions}/{n} | 평균 점수 차 {margin_avg}")


def main():
    data = json.loads((HERE / "cases.json").read_text())
    queries = json.loads((HERE / "eval.json").read_text())["queries"]
    id_map = json.loads(ID_MAP.read_text()) if ID_MAP.exists() else {}
    master = ensure_master(data)
    load(data, queries, master, id_map)
    wait_searchable(len(data["cases"]))
    evaluate(data, queries, id_map)


if __name__ == "__main__":
    main()
