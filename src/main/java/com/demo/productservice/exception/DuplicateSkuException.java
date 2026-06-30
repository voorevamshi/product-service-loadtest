package com.demo.productservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateSkuException extends RuntimeException {
    public DuplicateSkuException(String sku) {
        super("Product already exists with SKU: " + sku);
    }
}
