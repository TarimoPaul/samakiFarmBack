package com.samaki.farm.farm.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PUT /api/farms/{farmId} - jina na mahali pekee.
 *
 * MMILIKI haumo. Umiliki hauwekwi kwa kuandika `owner_user_id` moja kwa
 * moja - unatokana na uanachama (angalia FarmUserService), na kuuruhusu
 * ubadilishwe hapa kungeunda njia ya pili yenye sheria tofauti kwa jambo
 * lilelile.
 */
public record UpdateFarmRequest(
        @NotBlank(message = "Jina la shamba linahitajika") String name,
        String location
) {}
