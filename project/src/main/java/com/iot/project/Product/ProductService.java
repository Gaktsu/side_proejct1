package com.iot.project.Product;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        if (productRepository.existsByProductCode(request.productCode())) {
            throw new DuplicateProductCodeException(request.productCode());
        }
        try {
            // 동시 요청이 exists 검사를 함께 통과한 경우 DB UNIQUE 제약이 막는다
            return ProductResponse.from(productRepository.saveAndFlush(request.toEntity()));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateProductCodeException(request.productCode());
        }
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        try {
            productRepository.delete(product);
            // FK 위반을 커밋 시점이 아닌 여기서 감지하기 위해 즉시 flush
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ProductInUseException(id);
        }
    }
}
