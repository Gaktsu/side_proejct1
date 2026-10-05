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
        return ProductResponse.from(productRepository.save(request.toEntity()));
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
