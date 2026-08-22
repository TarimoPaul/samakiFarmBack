package com.samaki.farm.farm.dto;

/** ownerName inaweza kuwa null - shamba lililoundwa na msimamizi halina mmiliki bado. */
public record FarmSummary(Integer farmId, String name, String location, String ownerName) {}
