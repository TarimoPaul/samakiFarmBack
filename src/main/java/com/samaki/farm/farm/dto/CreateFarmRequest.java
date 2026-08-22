package com.samaki.farm.farm.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFarmRequest(
        @NotBlank(message = "Jina la shamba linahitajika") String name,
        String location
) {}
