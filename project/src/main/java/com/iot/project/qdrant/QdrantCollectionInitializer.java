package com.iot.project.qdrant;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// 기동 시 컬렉션 확인. Qdrant가 꺼져 있거나 설정이 다르면 앱 전체 기동이 실패한다 (모노리스 결합 확인용으로 의도적으로 유지)
@Component
@RequiredArgsConstructor
public class QdrantCollectionInitializer implements ApplicationRunner {

    private final QdrantClient qdrantClient;

    @Override
    public void run(ApplicationArguments args) {
        qdrantClient.ensureCollection();
    }
}
