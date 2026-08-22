package com.samaki.farm.productionunit.dto;

/** GraphQL input - angalia schema.graphqls (input CreateProductionUnitInput). */
public record CreateProductionUnitInput(String code, String type, Double sizeM3, String waterSource) {}
