package com.samaki.farm.feed.dto;

import com.samaki.farm.feed.entity.FeedType;

/**
 * Aina ya chakula pamoja na SABABU ya kuwa kwenye orodha.
 *
 * Daraja linarudishwa badala ya kuchujwa kimyakimya kwa sababu EXACT na
 * SAFE_LOWER si kitu kimoja: cha kwanza ndicho kilichokusudiwa, cha pili ni
 * mbadala unaokubalika. Mteja anapaswa kuweza kuonyesha tofauti hiyo badala
 * ya kuwasilisha vyote kana kwamba ni sawa.
 *
 * suitability HAIWEZI kuwa UNSAFE_HIGHER - hiyo huchujwa kabla ya hapa.
 */
public record SuitableFeedType(FeedType feedType, FeedSuitability suitability) {}
