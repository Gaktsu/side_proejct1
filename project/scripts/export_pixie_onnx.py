"""PIXIE-Rune-v1.0 → ONNX 변환 + 검증.

출력 디렉터리(기본 ~/models/pixie-rune-onnx)에 model.onnx, model.onnx_data, tokenizer.json을 만든다.
ONNX 출력은 CLS 토큰 벡터를 L2 정규화한 최종 임베딩 [batch, 1024] (sentence-transformers의
Transformer → Pooling(cls) → Normalize와 같음). Java는 이 값을 그대로 쓴다.

변환 후 sentence-transformers 결과와 코사인 유사도를 비교해 0.9999 미만이면 실패한다.
onnxruntime / tokenizers 버전을 올리거나 모델을 다시 변환하면 Java 쪽도 같은 비교를 다시 할 것.

필요 패키지 (벤치마크와 같은 버전):
    pip install torch==2.14.1 --index-url https://download.pytorch.org/whl/cpu
    pip install transformers==5.19.0 sentence-transformers==6.1.0 onnx onnxruntime==1.31.0 onnxscript

사용법:
    python scripts/export_pixie_onnx.py [출력 디렉터리]
"""
import shutil
import sys
import tempfile
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from huggingface_hub import hf_hub_download
from sentence_transformers import SentenceTransformer
from transformers import AutoModel, AutoTokenizer

MODEL = "telepix/PIXIE-Rune-v1.0"
SAMPLES = [
    "공정: 사출\n제품: ABS 제어기 하우징\n증상: 리브 주변 표면 함몰(싱크마크)\n"
    "관찰: 보압 설정 58→39 MPa 저하, 금형 온도 정상\n확정 원인: 보압 설정값 오입력으로 수축 보상 부족\n"
    "조치: 보압 58 MPa 복구 및 레시피 잠금",
    "query: ABS/PC 커버의 보스 주변 함몰이 급증했다. 보압 설정값 또는 유지 시간이 이전 승인 조건보다 낮게 기록되었다.",
]


class ClsNormalize(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask):
        cls = self.model(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state[:, 0]
        return torch.nn.functional.normalize(cls, dim=-1)


def export(out: Path):
    tokenizer = AutoTokenizer.from_pretrained(MODEL)
    model = AutoModel.from_pretrained(MODEL).eval()
    x = tokenizer(SAMPLES, padding=True, return_tensors="pt")
    with tempfile.TemporaryDirectory() as tmp:
        # 2GB가 넘어 가중치가 텐서별 파일로 흩어지므로, 다시 읽어 model.onnx_data 하나로 합친다
        raw = Path(tmp) / "model.onnx"
        torch.onnx.export(
            ClsNormalize(model), (x["input_ids"], x["attention_mask"]), str(raw),
            input_names=["input_ids", "attention_mask"], output_names=["embedding"],
            dynamic_axes={"input_ids": {0: "batch", 1: "seq"}, "attention_mask": {0: "batch", 1: "seq"},
                          "embedding": {0: "batch"}},
            opset_version=17, dynamo=False, external_data=True,
        )
        out.mkdir(parents=True, exist_ok=True)
        (out / "model.onnx_data").unlink(missing_ok=True)  # onnx는 기존 파일 뒤에 이어 쓰므로 먼저 지운다
        onnx.save_model(onnx.load(raw), out / "model.onnx", save_as_external_data=True,
                        all_tensors_to_one_file=True, location="model.onnx_data")
        (out / "model.onnx_data").chmod(0o644)  # onnx가 600으로 만듦 → 컨테이너의 다른 사용자도 읽을 수 있게
    shutil.copyfile(hf_hub_download(MODEL, "tokenizer.json"), out / "tokenizer.json")


def verify(out: Path):
    expected = SentenceTransformer(MODEL, device="cpu").encode(SAMPLES, normalize_embeddings=True)
    session = ort.InferenceSession(str(out / "model.onnx"), providers=["CPUExecutionProvider"])
    tokenizer = AutoTokenizer.from_pretrained(MODEL)
    for text, e in zip(SAMPLES, expected):
        x = tokenizer([text], return_tensors="np")
        v = session.run(None, {"input_ids": x["input_ids"], "attention_mask": x["attention_mask"]})[0][0]
        cos = float(np.dot(v, e))
        print(f"cos={cos:.8f}  {text[:30]!r}")
        assert cos > 0.9999, "sentence-transformers 결과와 다름"


if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "~/models/pixie-rune-onnx").expanduser()
    export(out)
    verify(out)
    print("OK", out)
