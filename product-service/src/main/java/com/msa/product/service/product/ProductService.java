package com.msa.product.service.product;

import com.msa.product.dto.product.RequestProductDto;
import com.msa.product.dto.product.RequestProductOptionDto;
import com.msa.product.entity.product.ProductInventory;
import com.msa.product.entity.product.ProductOption;
import com.msa.product.entity.product.Products;
import com.msa.product.repository.product.ProductInventoryRepository;
import com.msa.product.repository.product.ProductOptionRepository;
import com.msa.product.repository.product.ProductsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductService {
    private final ProductsRepository productsRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductInventoryRepository productInventoryRepository;

    @Transactional
    public void createProduct(RequestProductDto requestProductDto){
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        Products createProductResult = saveProduct(requestProductDto, userId);
        saveProductOptionAndInventory(createProductResult, requestProductDto);
    }

    private Products saveProduct(RequestProductDto requestProductDto, String userId){
        Products products = Products.create(
                requestProductDto.productCode(),
                requestProductDto.productName(),
                requestProductDto.description(),
                requestProductDto.brandName(),
                userId
        );
        return productsRepository.save(products);
    }

    private void saveProductOptionAndInventory(Products product, RequestProductDto requestProductDto){
        List<ProductOption> options = new ArrayList<>();
        List<ProductInventory> inventories = new ArrayList<>();

        for (RequestProductOptionDto optionDto : requestProductDto.productOptionDtos()) {
            ProductOption option = ProductOption.create(
                    product,
                    optionDto.optionName(),
                    optionDto.price(),
                    optionDto.currency()
            );

            options.add(option);
            inventories.add(
                    ProductInventory.create(
                            option,
                            optionDto.totalQuantity()
                    )
            );
        }

        productOptionRepository.saveAll(options);
        productInventoryRepository.saveAll(inventories);
    }
}
