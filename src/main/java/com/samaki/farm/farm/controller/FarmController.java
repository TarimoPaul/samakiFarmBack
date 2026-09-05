package com.samaki.farm.farm.controller;

import com.samaki.farm.common.web.ApiResponse;
import com.samaki.farm.farm.dto.CreateFarmRequest;
import com.samaki.farm.farm.dto.FarmSummary;
import com.samaki.farm.farm.dto.UpdateFarmRequest;
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

    /** Jina na mahali. Mmiliki hauguswi - unatokana na uanachama. */
    @PutMapping("/{farmId}")
    @PreAuthorize("hasAuthority('manage_farms')")
    public ApiResponse<FarmSummary> updateFarm(@PathVariable Integer farmId,
                                               @Valid @RequestBody UpdateFarmRequest req) {
        return ApiResponse.ok(farmService.update(farmId, req), "Shamba limehifadhiwa.");
    }

    /**
     * Ruhusa YAKE MWENYEWE (`delete_farm`), si `manage_farms`.
     *
     * Ndiyo endpoint pekee kwenye mfumo huu inayoondoa MUKTADHA wa kila kitu
     * kilichomo - vitengo, mizunguko, ulishaji, vipimo vya maji - hivyo mtu
     * anaweza kupewa uwezo wa kupanga mashamba bila kupewa uwezo wa
     * kuyafuta. Angalia V15__farm_delete_permission.sql.
     */
    @DeleteMapping("/{farmId}")
    @PreAuthorize("hasAuthority('delete_farm')")
    public ApiResponse<Void> deleteFarm(@PathVariable Integer farmId) {
        farmService.delete(farmId);
        return ApiResponse.ok(null, "Shamba limefutwa.");
    }
}
