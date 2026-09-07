package com.samaki.farm.feed.dto;

import com.samaki.farm.feed.entity.FeedPurchase;
import com.samaki.farm.feed.entity.FeedType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mstari mmoja wa manunuzi kama unavyorudishwa kwa MWOMBAJI HUYU - yaani
 * baada ya bei kufichwa kwa asiye na `view_feed_cost` (V18).
 *
 * KWA NINI DTO NA SI ENTITY ILIYOWEKWA NULL. Njia fupi ingekuwa
 * `purchase.setUnitCost(null)` kabla ya kurudisha. Ni hatari halisi, si ya
 * kinadharia: listPurchases ni @Transactional, hivyo FeedPurchase
 * inayorudishwa BADO IPO ndani ya persistence context. Hibernate ingeona
 * mabadiliko wakati wa flush na kujaribu kuandika NULL kwenye `unit_cost`
 * ambayo ni NOT NULL - yaani ufichaji wa kuonyesha ungefuta bei
 * DATABASE. Rekodi ya fedha isingerekebishika kwa sababu ya ruhusa ya
 * kusoma.
 *
 * Record ni ya kusomwa tu, hivyo mtego huo haupo kabisa. GraphQL haijali
 * class ya Java: `FeedPurchase` ya schema ni object type moja, na Spring
 * GraphQL inasoma property kwa jina kutoka kwa chochote kinachorudishwa.
 *
 * `masked(...)` ndiyo mahali PEKEE panapotengeneza mstari usio na bei -
 * ni njia moja badala ya kila call site kukumbuka kuweka null kwenye safu
 * MBILI. Kusahau moja kungeacha totalCost ikitangaza bei kwa kugawanya.
 */
public record FeedPurchaseView(
        Integer purchaseId,
        LocalDate purchaseDate,
        FeedType feedType,
        BigDecimal quantityKg,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String supplier,
        /**
         * Umebatilishwa kwa rekodi ya kurekebisha (movement ya OUT
         * inayouelekea). Unabaki kwenye orodha - ndiyo maana ya leja
         * isiyofutika - lakini kilo zake hazipo tena kwenye salio.
         *
         * Inatoka KWENYE LEJA, si kwenye safu ya feed_purchases: leja ndiyo
         * inayojua kilichotokea, na safu ya `reversed_at` ingekuwa nakala
         * yake inayoweza kuachana nayo.
         */
        boolean reversed) {

    /** Mstari kamili - kwa mwenye `view_feed_cost`. */
    public static FeedPurchaseView full(FeedPurchase purchase, boolean reversed) {
        return new FeedPurchaseView(
                purchase.getPurchaseId(),
                purchase.getPurchaseDate(),
                purchase.getFeedType(),
                purchase.getQuantityKg(),
                purchase.getUnitCost(),
                purchase.getTotalCost(),
                purchase.getSupplier(),
                reversed);
    }

    /**
     * Mstari usio na bei. Kinachobaki ni taarifa ya UENDESHAJI: kiasi,
     * aina, tarehe, muuzaji. Mstari WENYEWE haufichwi - kujua kwamba
     * magunia yaliingia ni sehemu ya kazi ya shambani; bei yake si.
     */
    public static FeedPurchaseView masked(FeedPurchase purchase, boolean reversed) {
        return new FeedPurchaseView(
                purchase.getPurchaseId(),
                purchase.getPurchaseDate(),
                purchase.getFeedType(),
                purchase.getQuantityKg(),
                null,
                null,
                purchase.getSupplier(),
                // Ubatilishaji SI bei. Mtu asiyeruhusiwa kuona gharama bado
                // anahitaji kujua kwamba magunia haya hayako ghalani -
                // vinginevyo angepanga kulisha kwa kilo zisizokuwepo.
                reversed);
    }
}
