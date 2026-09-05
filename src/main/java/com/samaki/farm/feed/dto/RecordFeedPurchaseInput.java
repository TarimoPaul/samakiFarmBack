package com.samaki.farm.feed.dto;

public record RecordFeedPurchaseInput(String purchaseDate, Integer feedTypeId, Double quantityKg,
                                       Double unitCost, String supplier) {}
