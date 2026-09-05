package com.samaki.farm.farm.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.farm.dto.CreateFarmRequest;
import com.samaki.farm.farm.dto.FarmSummary;
import com.samaki.farm.farm.dto.UpdateFarmRequest;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * B7 - API ya mashamba.
 *
 * Ilihitajika kwa sababu kujisajili HAKUUNDI shamba tena (B3). Bila hii,
 * hakuna njia yoyote ya kuunda shamba kupitia API - na kampuni inaweza
 * kuwa na zaidi ya moja.
 *
 * KUFUTA ni soft-delete, na kunakataliwa shamba likiwa bado na wanachama.
 * Sababu ni ya kiufundi na inayoonekana: safu za `farm_users` zingebaki
 * zikielekeza kwenye shamba ambalo @SQLRestriction inalificha kwenye kila
 * query - watu hao wangebaki na uanachama usio na shamba, na hakuna skrini
 * ingeeleza kwa nini mambo yameacha kufanya kazi. Ondoa watu kwanza, ndipo
 * ombi lilelile lipite.
 */
@Service
public class FarmService {

    private final FarmRepository farmRepository;
    private final FarmUserRepository farmUserRepository;
    private final PermissionChecker permissionChecker;

    public FarmService(FarmRepository farmRepository, FarmUserRepository farmUserRepository,
                       PermissionChecker permissionChecker) {
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.permissionChecker = permissionChecker;
    }

    @Transactional
    public FarmSummary create(CreateFarmRequest req) {
        Farm farm = new Farm();
        farm.setName(requireAvailableName(req.name(), null));
        farm.setLocation(normalise(req.location()));
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

    /**
     * Jina na mahali. HAIGUSI mmiliki wala wanachama.
     *
     * Umiliki hauandikwi hapa kwa makusudi: unatokana na uanachama
     * (FarmUserService), na njia ya pili yenye sheria tofauti kwa jambo
     * lilelile ni njia ya kupata hali mbili zisizolingana.
     */
    @Transactional
    public FarmSummary update(Integer farmId, UpdateFarmRequest req) {
        Farm farm = requireFarm(farmId);

        farm.setName(requireAvailableName(req.name(), farmId));
        farm.setLocation(normalise(req.location()));

        return toSummary(farmRepository.save(farm));
    }

    /**
     * Soft-delete ya shamba - INAKATALIWA likiwa bado na wanachama.
     *
     * Angalia maelezo ya darasa kwa sababu. Ujumbe unataja IDADI yao kwa
     * sababu ndiyo inayomweleza msimamizi kazi iliyobaki, na kikwazo hiki
     * kinapitika - si sheria ya kudumu kama ile ya mmiliki wa shamba.
     *
     * ROOT aliyekuwa amelichagua shamba hili haachwi kwenye hali mbovu:
     * JwtAuthFilter inathibitisha chaguo lake kwa `existsByFarmId`, ambayo
     * ni derived query - shamba lililofutwa linajibiwa "halipo", hivyo
     * chaguo linaanguka na kichagua-shamba kinarudi "Chagua shamba...".
     */
    @Transactional
    public void delete(Integer farmId) {
        Farm farm = requireFarm(farmId);

        long members = farmUserRepository.countByFarm_FarmId(farmId);
        if (members > 0) {
            throw new ConflictException(
                    "Shamba hili lina wanachama " + members + ". Watoe kwanza kabla ya kulifuta.",
                    ErrorCodes.FARM_IN_USE);
        }

        farm.softDelete(permissionChecker.currentUser().getUserId());
        farmRepository.save(farm);
    }

    /**
     * Shamba hili, likiwa BADO lipo.
     *
     * findByFarmId (derived) badala ya findById kwa makusudi: Hibernate
     * haitumii @SQLRestriction kwenye lookup ya moja kwa moja ya PK (angalia
     * BaseEntity), hivyo findById ingerudisha hata shamba lililofutwa - na
     * DELETE ingeweza kuitwa mara mbili ikiripoti mafanikio mara zote mbili.
     */
    private Farm requireFarm(Integer farmId) {
        return farmRepository.findByFarmId(farmId)
                .orElseThrow(() -> new IllegalArgumentException("Shamba halipo"));
    }

    /**
     * Jina lililopunguzwa nafasi tupu, likiwa halijachukuliwa.
     *
     * Ukaguzi unalingana na kikwazo cha database tangu V14: ni partial index
     * (`WHERE is_deleted = false`), na derived queries hapa nazo zinachujwa
     * vivyo hivyo - hivyo jina la shamba lililofutwa linaachiwa huru
     * kutumika tena, kwenye pande zote mbili.
     *
     * Awali hapakuwa na ukaguzi hata kidogo: jina lililojirudia lilifika
     * database na kurudi kama sentensi ya jumla kuhusu "vikwazo vya
     * database", isiyomwambia msimamizi kwamba tatizo ni jina tu.
     */
    private String requireAvailableName(String raw, Integer selfId) {
        String name = raw == null ? "" : raw.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Jina la shamba linahitajika.");
        }
        boolean taken = selfId == null
                ? farmRepository.existsByName(name)
                : farmRepository.existsByNameAndFarmIdNot(name, selfId);
        if (taken) {
            throw new ConflictException("Shamba lenye jina hili tayari lipo.");
        }
        return name;
    }

    /** Mahali tupu ni KUTOKUWA na mahali, si mahali pasipo na herufi. */
    private static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static FarmSummary toSummary(Farm farm) {
        return new FarmSummary(farm.getFarmId(), farm.getName(), farm.getLocation(),
                farm.getOwner() == null ? null : farm.getOwner().getName());
    }
}
