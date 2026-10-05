package com.iot.project.ManufacturingProcess;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManufacturingProcessService {

    private final ManufacturingProcessRepository manufacturingProcessRepository;

    @Transactional
    public ManufacturingProcessResponse create(ManufacturingProcessCreateRequest request) {
        if (manufacturingProcessRepository.existsByProcessCode(request.processCode())) {
            throw new DuplicateProcessCodeException(request.processCode());
        }
        return ManufacturingProcessResponse.from(manufacturingProcessRepository.save(request.toEntity()));
    }

    @Transactional
    public void delete(Long id) {
        ManufacturingProcess process = manufacturingProcessRepository.findById(id)
                .orElseThrow(() -> new ManufacturingProcessNotFoundException(id));
        try {
            manufacturingProcessRepository.delete(process);
            // FK 위반을 커밋 시점이 아닌 여기서 감지하기 위해 즉시 flush
            manufacturingProcessRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ManufacturingProcessInUseException(id);
        }
    }
}
