package com.msa.product.dto.product;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RequestProductDto (
        String productCode,
        String productName,
        String description,
        String brandName,
        @NotEmpty
        List<RequestProductOptionDto> productOptionDtos
){
}
