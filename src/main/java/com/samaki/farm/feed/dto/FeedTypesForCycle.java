package com.samaki.farm.feed.dto;

import java.util.List;

/**
 * Jibu la `feedTypesForCycle`: chakula kinachofaa mzunguko huu LEO.
 *
 * noSuitableFeed ni bendera yake YENYEWE, si `feedTypes.isEmpty()`
 * inayokisiwa na mteja, kwa sababu orodha tupu hapa ina maana mahususi na
 * mbaya: KILA aina iliyopo kwenye katalogi ni ya samaki wakubwa kuliko
 * hawa. Yaani hakuna kosa la mfumo - kuna pengo halisi kwenye katalogi, na
 * mtu anayelisha anapaswa kuambiwa hivyo badala ya kuonyeshwa orodha tupu
 * isiyo na maelezo.
 *
 * cycleAgeMonths inarudishwa ili mteja aweze kuonyesha kwa nini uamuzi
 * umefikiwa ("miezi 1") bila kukokotoa umri upya - hesabu hiyo ni ya
 * server, mahali pamoja (FeedService.cycleAgeMonths).
 */
public record FeedTypesForCycle(int cycleAgeMonths,
                                boolean noSuitableFeed,
                                List<SuitableFeedType> feedTypes) {}
