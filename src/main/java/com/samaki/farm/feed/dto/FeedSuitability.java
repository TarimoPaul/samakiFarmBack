package com.samaki.farm.feed.dto;

/**
 * Uhusiano kati ya dirisha la umri la aina ya chakula [min, max] na umri
 * halisi wa mzunguko (A). Angalia FeedService.classify - hesabu iko HUKO,
 * mahali pamoja, ndiyo inayojaribiwa.
 *
 * MWELEKEO SI SAWA PANDE ZOTE MBILI, na ndiyo sababu enum hii ipo badala ya
 * bendera ya kweli/uongo: samaki mkubwa anaweza kula chakula cha wadogo
 * (kidogo, kinameng'enyeka), lakini mdogo HAWEZI kula cha wakubwa - punje
 * kubwa kuliko mdomo wake ni kukosa chakula au kukwama.
 */
public enum FeedSuitability {

    /** min <= A <= max - chakula kilichokusudiwa umri huu. */
    EXACT,

    /** max < A - chakula cha wadogo kuliko hawa. Kinakubalika. */
    SAFE_LOWER,

    /**
     * min > A - chakula cha wakubwa kuliko hawa. HAKIRUDISHWI KAMWE kwa
     * mteja (angalia FeedService.feedTypesForCycle): kitu ambacho mfumo
     * unajua ni hatari hakipaswi kuonekana kwenye orodha ya kuchagua.
     */
    UNSAFE_HIGHER
}
