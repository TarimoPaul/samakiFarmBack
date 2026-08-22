package com.samaki.farm.cycle.dto;

/** GraphQL input - angalia schema.graphqls (input CreateCycleInput). */
public record CreateCycleInput(Integer unitId, Integer speciesId, String stockingDate,
                                Integer fingerlingsCount, Double survivalRateEstimate) {}
