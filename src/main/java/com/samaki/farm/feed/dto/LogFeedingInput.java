package com.samaki.farm.feed.dto;

/** GraphQL input - angalia schema.graphqls (input LogFeedingInput). */
public record LogFeedingInput(Integer cycleId, String logDate, String feedType, Double quantityKg) {}
