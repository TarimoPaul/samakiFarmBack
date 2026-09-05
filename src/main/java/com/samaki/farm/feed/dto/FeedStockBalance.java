package com.samaki.farm.feed.dto;

import com.samaki.farm.feed.entity.FeedType;

import java.math.BigDecimal;

/**
 * Salio la stoo kwa AINA MOJA ya chakula, ndani ya shamba moja.
 *
 * Awali salio lilikuwa namba moja ya shamba zima. Ilikuwa ikidanganya:
 * kilo 50 za chakula cha vifaranga pamoja na kilo -50 za cha wakubwa
 * zilijumlika hadi 0, hivyo ghala lenye chakula lilionekana tupu kabisa.
 * Chakula hakibadilishani - kilo za aina moja haziwezi kulisha samaki
 * wanaohitaji aina nyingine - hivyo kila aina ina salio lake.
 */
public record FeedStockBalance(FeedType feedType, BigDecimal quantityKg) {}
