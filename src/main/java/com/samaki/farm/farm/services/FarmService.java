package com.samaki.farm.farm.services;

import com.samaki.farm.farm.dto.CreateFarmRequest;
import com.samaki.farm.farm.dto.FarmSummary;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * B7 - API ndogo ya mashamba.
 *
 * Ilihitajika kwa sababu kujisajili HAKUUNDI shamba tena (B3). Bila hii,
 * hakuna njia yoyote ya kuunda shamba kupitia API - na kampuni inaweza
 * kuwa na zaidi ya moja.
 *
 * Kubadilisha jina/mahali hakujajengwa bado - halikuwa la lazima kwa
 * awamu hii.
 */
@Service
public class FarmService {

    private final FarmRepository farmRepository;

    public FarmService(FarmRepository farmRepository) {
        this.farmRepository = farmRepository;
    }

    @Transactional
    public FarmSummary create(CreateFarmRequest req) {
        Farm farm = new Farm();
        farm.setName(req.name());
        farm.setLocation(req.location());
        // owner inabaki null: mmiliki anawekwa kwa kumpa mtu uanachama wa
        // shamba hili (angalia FarmUserService), si wakati wa kuliunda.
        return toSummary(farmRepository.save(farm));
    }

    /**
     * Mashamba YOTE ya kampuni. Haichujwi kwa shamba la mwombaji kwa
     * makusudi: mwenye manage_farms ndiye anayepanga mashamba, hivyo
     * lazima ayaone yote ili aweze kupanga watu kwenye jipya.
     */
    @Transactional(readOnly = true)
    public List<FarmSummary> listAll() {
        return farmRepository.findAll().stream().map(FarmService::toSummary).toList();
    }

    private static FarmSummary toSummary(Farm farm) {
        return new FarmSummary(farm.getFarmId(), farm.getName(), farm.getLocation(),
                farm.getOwner() == null ? null : farm.getOwner().getName());
    }
}
