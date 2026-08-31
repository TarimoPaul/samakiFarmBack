package com.samaki.farm.waterquality.dto;

/**
 * Kipimo kimoja kinachorekodiwa.
 *
 * Vipimo vyote vinne vinaruhusiwa kuwa null kwa sababu safu zake ni
 * nullable kwenye schema, na hivyo ndivyo shambani ilivyo: mtu mwenye
 * kipima-pH pekee anarekodi pH, na kuacha nyingine wazi ni sahihi zaidi
 * kuliko kuandika sifuri.
 *
 * logDate ikiachwa wazi inatumia leo - mtindo ule ule wa LogFeedingInput.
 */
public record LogWaterQualityInput(
        Integer unitId,
        String logDate,
        Double ph,
        Double temperature,
        Double oxygen,
        Double ammonia,
        String notes
) {}
