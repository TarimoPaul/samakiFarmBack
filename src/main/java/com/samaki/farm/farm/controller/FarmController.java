package com.samaki.farm.farm.controller;

import com.samaki.farm.common.web.ApiResponse;
import com.samaki.farm.farm.dto.CreateFarmRequest;
import com.samaki.farm.farm.dto.FarmSummary;
import com.samaki.farm.farm.services.FarmService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/farms")
public class FarmController {

    private final FarmService farmService;

    public FarmController(FarmService farmService) {
        this.farmService = farmService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manage_farms')")
    public ApiResponse<FarmSummary> createFarm(@Valid @RequestBody CreateFarmRequest req) {
        return ApiResponse.ok(farmService.create(req), "Shamba limeundwa.");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('manage_farms')")
    public ApiResponse<List<FarmSummary>> listFarms() {
        return ApiResponse.ok(farmService.listAll());
    }
}
