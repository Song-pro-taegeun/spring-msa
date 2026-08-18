package com.msa.product.repository.product;

import com.msa.product.entity.product.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
}
