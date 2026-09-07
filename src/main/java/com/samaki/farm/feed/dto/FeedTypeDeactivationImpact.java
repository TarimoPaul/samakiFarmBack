package com.samaki.farm.feed.dto;

import java.math.BigDecimal;

/**
 * Kinachopotea kikizimwa - ONYO, SI KIKWAZO.
 *
 * setFeedTypeActive HAIKATAI kuzima kwa sababu ya stoo wala mizunguko
 * (angalia FeedService.setFeedTypeActive): kuzima ni kitendo kinachorudishwa
 * nyuma, na aina isiyofaa tena kwa shamba inaweza kabisa kuwa bado ina kilo
 * ghalani. Kikwazo hapa kingemfungia msimamizi nje ya usimamizi wake
 * mwenyewe.
 *
 * Lakini "inarudishwa nyuma" si sawa na "haina athari". Query hii inampa
 * anayebofya namba MBILI anazopaswa kuziona kabla, si baada:
 *
 *  - kilo zilizobaki  - stoo iliyolipiwa ambayo haitachaguliwa tena kwenye
 *    skrini ya ulishaji hadi aina irudishwe.
 *  - mizunguko tegemezi - samaki ambao, KESHO ASUBUHI, hawatakuwa na
 *    chakula KILICHOKUSUDIWA umri wao kwenye orodha.
 *
 * Namba zote mbili ni za SHAMBA LA MWOMBAJI. Katalogi yenyewe ni ya kimfumo,
 * lakini athari ya kuizima ni ya shamba: kilo ziko kwenye ghala fulani, na
 * samaki wako kwenye tanki fulani.
 */
public record FeedTypeDeactivationImpact(
        BigDecimal remainingKg,
        int dependentActiveCycleCount) {}
