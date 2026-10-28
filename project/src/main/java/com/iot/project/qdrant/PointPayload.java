package com.iot.project.qdrant;

// Qdrant 포인트에 함께 저장하는 값. sourceVersion으로 MySQL 사례 버전과 대조하고, searchable=false면 검색에서 빠진다
public record PointPayload(int sourceVersion, boolean searchable) {
}
