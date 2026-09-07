package com.samaki.farm.cycle.dto;

/**
 * GraphQL input - angalia schema.graphqls (input CreateCycleInput).
 *
 * `stockingAgeMonths` ni HIARI (null = 0, yaani wadogo kabisa), hivyo
 * mteja wa zamani asiyeituma anaendelea kufanya kazi bila mabadiliko na
 * kupata hesabu ile ile aliyokuwa akipata.
 */
public record CreateCycleInput(Integer unitId, Integer speciesId, String stockingDate,
                                Integer fingerlingsCount, Double survivalRateEstimate,
                                Integer stockingAgeMonths) {}
