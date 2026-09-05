package com.samaki.farm.feed.dto;

public record LogFeedingInput(Integer cycleId, String logDate, Integer feedTypeId, Double quantityKg) {}
