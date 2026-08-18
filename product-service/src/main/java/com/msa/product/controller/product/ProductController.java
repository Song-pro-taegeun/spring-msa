package com.msa.product.controller.product;

import com.msa.product.dto.product.RequestProductDto;
import com.msa.product.service.product.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/product")
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public ResponseEntity<Void> createProduct(
            @Valid @RequestBody RequestProductDto requestProductDto
    ){
        productService.createProduct(requestProductDto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
