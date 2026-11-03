package com.companyos.product;

import java.util.Set;

public record CreateProductCommand(String productKey, String name, String description, Set<String> featureKeys) {
}
