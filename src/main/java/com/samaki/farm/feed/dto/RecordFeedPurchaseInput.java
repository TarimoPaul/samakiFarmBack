package com.samaki.farm.feed.dto;

/** GraphQL input - angalia schema.graphqls (input RecordFeedPurchaseInput). */
public record RecordFeedPurchaseInput(String purchaseDate, String feedType, Double quantityKg,
                                       Double unitCost, String supplier) {}
