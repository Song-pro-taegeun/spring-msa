package com.msa.product.repository.product;

import com.msa.product.entity.product.Products;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductsRepository extends JpaRepository<Products, Long> {
}
