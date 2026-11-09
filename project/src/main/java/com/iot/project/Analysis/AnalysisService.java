package com.iot.project.Analysis;

import com.iot.project.AnalysisCandidate.AnalysisCandidateRepository;
import com.iot.project.QualityCase.CaseStatus;
import com.iot.project.QualityCase.QualityCase;
import com.iot.project.QualityCase.QualityCaseDocument;
import com.iot.project.QualityCase.QualityCaseNotFoundException;
import com.iot.project.QualityCase.QualityCaseRepository;
import com.iot.project.QualityCase.TokenCheckUnavailableException;
import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.embedding.EmbeddingInputTooLongException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final AnalysisCandidateRepository candidateRepository;
    private final QualityCaseRepository qualityCaseRepository;
    private final EmbeddingClient embeddingClient;

    // 접수만 하고 바로 응답한다. 검색(임베딩 → Qdrant → 후보 저장)은 커밋 뒤 AnalysisWorker가 트랜잭션 밖에서
    // 사례 행을 잠가(FOR UPDATE) 같은 사례의 동시 요청을 한 줄로 세운다 → 뒤 요청은 앞 요청이 만든 분석을 보고 409
    // 검사 순서: 없음·삭제 404 → RESOLVED 409 → 진행 중 409 → 토큰 초과 400. 어느 것이든 실패하면 롤백되어 기존 분석은 그대로
    @Transactional
    public AnalysisAcceptedResponse request(Long qualityCaseId) {
        QualityCase qualityCase = qualityCaseRepository.findByIdForUpdate(qualityCaseId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new QualityCaseNotFoundException(qualityCaseId));
        if (qualityCase.getStatus() == CaseStatus.RESOLVED) {
            throw new ResolvedQualityCaseAnalysisException(qualityCaseId);
        }
        Optional<Analysis> existing = analysisRepository.findByQualityCaseId(qualityCaseId);
        if (existing.isPresent() && existing.get().getAnalysisStatus().isInProgress()) {
            throw new AnalysisAlreadyRequestedException(qualityCaseId, existing.get().getId());
        }
        checkQueryLength(qualityCase);

        // 끝난 분석은 갱신하지 않고 지운 뒤 새로 만든다 (이전 후보가 섞여 남지 않게)
        existing.ifPresent(old -> {
            candidateRepository.deleteByAnalysisIdNow(old.getId());
            analysisRepository.deleteNow(old.getId());
        });
        return AnalysisAcceptedResponse.from(analysisRepository.save(new Analysis(qualityCase)));
    }

    // 요청 뒤 OPEN 수정으로 길어질 수 있어 처리기에서도 초과가 날 수 있다 (그때는 FAILED)
    private void checkQueryLength(QualityCase qualityCase) {
        try {
            embeddingClient.checkQueryLength(QualityCaseDocument.toQueryText(qualityCase));
        } catch (EmbeddingInputTooLongException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TokenCheckUnavailableException(qualityCase.getId(), e);
        }
    }

    // 대상 사례가 삭제됐으면 404 (복구하면 다시 보인다)
    @Transactional(readOnly = true)
    public AnalysisResponse findById(Long id) {
        Analysis analysis = analysisRepository.findWithCaseById(id)
                .filter(a -> !a.getQualityCase().isDeleted())
                .orElseThrow(() -> new AnalysisNotFoundException(id));
        return AnalysisResponse.from(analysis, candidateRepository.findWithCaseByAnalysisId(id));
    }
}
