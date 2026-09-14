package com.samaki.farm.farm.services;

import com.samaki.farm.auth.security.AuthenticatedUser;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MASHAMBA YA MWOMBAJI - chanzo KIMOJA cha jibu la swali "mtu huyu ni wa
 * mashamba yapi?".
 *
 * =====================================================================
 * KWA NINI IPO, NA KWA NINI NI MOJA
 *
 * Kila module ya data ya uzalishaji inaanza na
 * {@code permissionChecker.requireFarmScope(...)}: farmId MOJA ya
 * principal, kila kitu kinachujwa kwayo. Madaftari ya KAMPUNI - mali
 * (V21/V22) na gharama (V23/V24) - hayafanyi hivyo: mmiliki mwenye
 * mashamba matatu ana swali MOJA ("nimewekeza/nimetumia kiasi gani, na
 * wapi?"), na jibu lake ni orodha moja inayovuka mashamba yote.
 *
 * Swali hilo lilikuwa limeandikwa ndani ya AssetService kama method
 * binafsi. Module ya gharama ilipolihitaji, chaguo lilikuwa mawili:
 * kuiandika tena, au kuihamisha hapa. Kuiandika tena kungekuwa nafasi ya
 * PILI ya kuvujisha shamba lililofutwa - angalia
 * FarmUserRepository.findFarmIdsByUserId, ambapo `join fu.farm f` ya
 * WAZI ndiyo pekee inayohakikisha @SQLRestriction inatumika. Sheria
 * ikiwa mahali pamoja, module ya nne inaiita badala ya kuisahau.
 *
 * VYANZO VIWILI kwa sababu ni dhana mbili tofauti kwenye schema, na
 * hakuna kikwazo kinachohakikisha kwamba mmiliki ni mwanachama pia
 * (FarmService.create inaunda shamba lisilo na mmiliki; umiliki
 * unawekwa baadaye). Kwa vitendo huwa ni watu wale wale, na
 * LinkedHashSet inaondoa rudufu ikihifadhi mpangilio wa farmId.
 *
 * ROOT: hana uanachama wowote (angalia PermissionChecker), hivyo vyanzo
 * vyote viwili ni tupu. Anachopata ni shamba ALILOLICHAGUA kwa kichwa
 * X-Farm-Id, likiwa limekwisha thibitishwa na JwtAuthFilter - hoja ile
 * ile ya D-9: bila hii, msimamizi mkuu wa mfumo ndiye PEKEE
 * asiyeweza kuona daftari la shamba lolote.
 *
 * HAKUNA RUHUSA INAYOKAGULIWA HAPA kwa makusudi. Darasa hili linajibu
 * UANACHAMA ("daftari LIPI"), si mamlaka ("unaruhusiwa kuona daftari") -
 * hilo la pili ni la {@code permissionChecker.require(...)} kwenye kila
 * service. Kuviunganisha kungefanya `myFarms` (isiyo na ruhusa) na
 * `assets`/`costs` (zenye ruhusa tofauti) zishindwe kutumia njia moja.
 * =====================================================================
 */
@Service
public class FarmMembershipService {

    private final FarmRepository farmRepository;
    private final FarmUserRepository farmUserRepository;
    private final PermissionChecker permissionChecker;

    public FarmMembershipService(FarmRepository farmRepository,
                                 FarmUserRepository farmUserRepository,
                                 PermissionChecker permissionChecker) {
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * VITAMBULISHO vya mashamba yote ya mwombaji - uanachama
     * (`farm_users`) PAMOJA na umiliki (`farms.owner_user_id`).
     *
     * Vyote viwili ni derived queries, hivyo @SQLRestriction ya Farm
     * inazichuja: shamba lililofutwa haliingii, hata kama safu ya
     * uanachama wake bado imesimama.
     */
    @Transactional(readOnly = true)
    public List<Integer> callersFarmIds() {
        AuthenticatedUser user = permissionChecker.currentUser();

        if (user.isRoot()) {
            return user.getFarmId() == null ? List.of() : List.of(user.getFarmId());
        }

        Set<Integer> farmIds = new LinkedHashSet<>(
                farmUserRepository.findFarmIdsByUserId(user.getUserId()));
        farmRepository.findByOwner_UserId(user.getUserId())
                .forEach(farm -> farmIds.add(farm.getFarmId()));
        return List.copyOf(farmIds);
    }

    /**
     * Mashamba yenyewe, si vitambulisho - kwa kichagua-shamba
     * (Query.myFarms).
     *
     * Orodha tupu HAIFIKI database: `farm_id IN ()` si SQL halali.
     */
    @Transactional(readOnly = true)
    public List<Farm> callersFarms() {
        List<Integer> farmIds = callersFarmIds();
        if (farmIds.isEmpty()) {
            return List.of();
        }
        return farmRepository.findByFarmIdInOrderByFarmIdAsc(farmIds);
    }

    /**
     * Shamba lililoombwa, likiwa ni LA MWOMBAJI - ukaguzi wa kila farmId
     * inayotoka kwa mteja kwenye madaftari ya kampuni.
     *
     * SI {@code permissionChecker.requireResourceInCallersFarm}, ambayo
     * inalinganisha na farmId MOJA ya principal: mmiliki mwenye mashamba
     * mawili angezuiwa kuandika kwenye shamba lake la pili, kwa sababu
     * principal inashikilia la kwanza pekee (TODO ya farm switching kwenye
     * JwtAuthFilter). Ukaguzi ni uanachama halisi kutoka database - sheria
     * ile ile, chanzo pana zaidi.
     *
     * Shamba lisilo lake linajibiwa AccessDeniedException (FORBIDDEN), si
     * "halipo": ni jibu lile lile requireResourceInCallersFarm inalotoa,
     * na kutofautisha "halipo" na "si lako" kungemwambia mgeni ni
     * mashamba mangapi yapo.
     */
    @Transactional(readOnly = true)
    public Farm requireCallersFarm(Integer farmId) {
        if (farmId == null) {
            throw new IllegalArgumentException("Shamba linahitajika.");
        }
        if (!callersFarmIds().contains(farmId)) {
            throw new AccessDeniedException("Huruhusiwi kufikia shamba hili.");
        }
        // findByFarmId (derived), si findById: @SQLRestriction inatumika
        // hapo pekee (angalia BaseEntity). Kwa vitendo callersFarmIds
        // tayari imechuja yaliyofutwa, hivyo hii ni ngome ya pili.
        return farmRepository.findByFarmId(farmId)
                .orElseThrow(() -> new IllegalArgumentException("Shamba halipo"));
    }
}
