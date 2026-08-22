package com.samaki.farm.waterquality.dto;

/** GraphQL input - angalia schema.graphqls (input LogWaterQualityInput). */
public record LogWaterQualityInput(Integer unitId, String logDate, Double ph, Double temperature,
                                    Double oxygen, String notes) {}
