package com.samaki.farm.cost.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.cost.dto.CycleRef;
import com.samaki.farm.cost.entity.Cost;
import com.samaki.farm.cost.entity.CostCategory;
import com.samaki.farm.cost.repository.CostCategoryRepository;
import com.samaki.farm.cost.repository.CostRepository;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.services.FarmMembershipService;
import com.samaki.farm.reminder.config.ReminderProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * GHARAMA ZA UENDESHAJI - fedha iliyotoka, ikaisha.
 *
 * =====================================================================
 * NI DAFTARI LA KAMPUNI, KAMA MALI - NA KWA SABABU ILE ILE
 *
 * Muundo wake ni wa AssetService neno kwa neno: {@code require(PERMISSION)}
 * kisha uanachama unakokotolewa kwa
 * {@link FarmMembershipService#callersFarmIds()}, SI
 * {@code requireFarmScope}. Ruhusa inasema "unaruhusiwa kuona daftari";
 * uanachama unasema "daftari LIPI". Mmiliki mwenye mashamba matatu ana
 * swali moja - "nimetumia kiasi gani, na wapi?" - na kuchuja kwa shamba
 * teule kungegeuza jibu lake kuwa la shamba moja.
 *
 * TOFAUTI YA KWELI NA MALI: MZUNGUKO.
 *
 * Mali ina shamba, mwisho. Gharama ina shamba NA - kwa hiari - mzunguko:
 *
 *   * cycleId = null -> gharama ya SHAMBA ZIMA (umeme, mshahara, kodi).
 *   * cycleId imewekwa -> gharama ya MZUNGUKO HUO (dawa, usafiri wa
 *     mavuno haya).
 *
 * Hiyo ndiyo safu inayoruhusu frontend kujibu maswali mawili tofauti
 * kutoka jedwali moja: "mzunguko huu umegharimu kiasi gani?" (mistari ya
 * mzunguko huo) na "shamba limetumia kiasi gani?" (mistari YOTE ya
 * shamba, ya mzunguko na ya shamba zima pamoja).
 *
 * HAIHUSIANI NA GHARAMA YA CHAKULA, na hilo si bahati mbaya:
 * `feeding_logs` inaunganisha chakula na mzunguko kwa KILO pekee (haina
 * safu ya fedha), na `feed_purchases` ina fedha lakini haina cycle_id.
 * Yaani gharama ya chakula haijawahi kuhesabiwa kwa mzunguko popote
 * kwenye mfumo huu. Hizi ni NYONGEZA juu ya hilo - angalia V23 kwa
 * hatari inayobaki (mtumiaji kuunda aina iitwayo "Chakula" kisha
 * kuandika manunuzi mara mbili), ambayo ni ya UI kuizuia, si ya backend.
 *
 * HAKUNA JUMLA HAPA - wala haipaswi kuwepo bado. Angalia CostResolver.
 * =====================================================================
 */
@Service
public class CostService {

    /** Lango la module NZIMA - kusoma na kuandika (angalia V24). */
    private static final String PERMISSION = "manage_costs";

    /** `cost_categories.name` ni VARCHAR(80) (V23). */
    private static final int CATEGORY_NAME_MAX_LENGTH = 80;

    /** `costs.amount` ni NUMERIC(14,2): precision - scale inaishia 999999999999.99. */
    private static final int AMOUNT_SCALE = 2;
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("999999999999.99");

    private final CostRepository costRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final CycleRepository cycleRepository;
    private final FarmMembershipService farmMembership;
    private final PermissionChecker permissionChecker;
    private final ReminderProperties reminderProperties;

    public CostService(CostRepository costRepository,
                       CostCategoryRepository costCategoryRepository,
                       CycleRepository cycleRepository,
                       FarmMembershipService farmMembership,
                       PermissionChecker permissionChecker,
                       ReminderProperties reminderProperties) {
        this.costRepository = costRepository;
        this.costCategoryRepository = costCategoryRepository;
        this.cycleRepository = cycleRepository;
        this.farmMembership = farmMembership;
        this.permissionChecker = permissionChecker;
        this.reminderProperties = reminderProperties;
    }

    // ==================================================== katalogi

    /**
     * Aina zote za gharama - katalogi ya kimfumo, kwa mpangilio wa jina.
     *
     * Inalindwa na {@code manage_costs} ile ile ya kuandika, kwa hoja ile
     * ile ya AssetService.listCategories: mtumiaji pekee wa orodha hii ni
     * fomu ya kurekodi gharama, ambayo tayari ni ya `manage_costs`.
     * Kuifungua zaidi kungefungua kitu kisicho na anayekitumia.
     */
    @Transactional(readOnly = true)
    public List<CostCategory> listCategories() {
        permissionChecker.require(PERMISSION);
        return costCategoryRepository.findAllByOrderByNameAsc();
    }

    /**
     * Aina mpya kwenye katalogi.
     *
     * {@code require}, SI {@code requireFarmScope}: `cost_categories`
     * haina farm_id (V23). Sheria ile ile ya AssetService.createCategory
     * na SpeciesService.create - kudai muktadha wa shamba kwa kitu kisicho
     * na shamba ni kuzuia usimamizi wa katalogi ya kimfumo kwa sababu
     * isiyohusiana nayo.
     */
    @Transactional
    public CostCategory createCategory(String name) {
        permissionChecker.require(PERMISSION);

        CostCategory category = new CostCategory();
        category.setName(requireAvailableCategoryName(name));
        return costCategoryRepository.save(category);
    }

    // ==================================================== daftari

    /**
     * Gharama za MASHAMBA YOTE ya mwombaji, mpya kwanza.
     *
     * Orodha tupu ya mashamba haifiki database: `farm_id IN ()` si SQL
     * halali. Mtu aliyeidhinishwa asiye na shamba lolote (hali HALALI -
     * angalia JwtAuthFilter) anapata orodha tupu, si hitilafu. HAKUNA
     * NO_FARM_CONTEXT hapa kwa makusudi, kama `assets`: msimbo huo
     * unamaanisha "ombi lako halina maana bila shamba teule", na daftari
     * la kampuni halina shamba teule hata kidogo.
     */
    @Transactional(readOnly = true)
    public List<Cost> listCosts() {
        permissionChecker.require(PERMISSION);

        List<Integer> farmIds = farmMembership.callersFarmIds();
        if (farmIds.isEmpty()) {
            return List.of();
        }
        return costRepository.findByFarm_FarmIdInOrderByCostDateDescCostIdDesc(farmIds);
    }

    /**
     * Mizunguko ya shamba MOJA - kichagua-mzunguko cha fomu ya gharama.
     *
     * INAYOENDELEA NA ILIYOFUNGWA, zote. Query iliyokuwepo
     * (`cycles`) haitoshi kwa mambo MAWILI, na kila moja peke yake
     * lingehitaji query mpya:
     *
     *   1. Ni ya SHAMBA MOJA TEULE (requireFarmScope), ilhali fomu ya
     *      gharama inaruhusu kuchagua lolote kati ya mashamba ya
     *      mwombaji. Mmiliki wa mashamba mawili aliyechagua shamba la
     *      pili asingepata mizunguko yake kabisa - ni tatizo lile lile la
     *      `myFarms`, hatua moja ndani zaidi.
     *   2. Inarudisha Cycle nzima bila lebo; kichagua kinahitaji
     *      maandishi ya kusomeka (angalia CycleRef).
     *
     * farmId INATOKA KWA MTEJA, hivyo inathibitishwa kwa uanachama
     * halisi - shamba lisilo lake ni FORBIDDEN, si orodha tupu.
     */
    @Transactional(readOnly = true)
    public List<CycleRef> listFarmCycles(Integer farmId) {
        permissionChecker.require(PERMISSION);
        Farm farm = farmMembership.requireCallersFarm(farmId);

        return cycleRepository
                .findByUnit_Farm_FarmIdOrderByStockingDateDescCycleIdDesc(farm.getFarmId())
                .stream()
                .map(CycleRef::of)
                .toList();
    }

    /**
     * Kurekodi gharama mpya.
     *
     * farmId INATOKA KWA MTEJA, hivyo lazima ithibitishwe - lakini SI kwa
     * {@code requireResourceInCallersFarm}, ambayo inalinganisha na farmId
     * MOJA ya principal. Mmiliki mwenye mashamba mawili angezuiwa
     * kurekodi gharama ya shamba lake la pili (angalia
     * FarmMembershipService.requireCallersFarm).
     *
     * MZUNGUKO NI WA HIARI: null ni gharama ya shamba zima, na ndiyo hali
     * ya kawaida kwa umeme na mishahara. Ukiwekwa, unakaguliwa kwa shamba
     * lililochaguliwa - angalia requireCycleInFarm.
     */
    @Transactional
    public Cost createCost(Integer farmId, Integer cycleId, Integer costCategoryId,
                           Double amount, String costDate, String description) {
        permissionChecker.require(PERMISSION);

        Farm farm = farmMembership.requireCallersFarm(farmId);

        Cost cost = new Cost();
        cost.setFarm(farm);
        cost.setCycle(requireCycleInFarm(cycleId, farm));
        cost.setCostCategory(requireCategory(costCategoryId));
        cost.setAmount(requireAmount(amount));
        cost.setCostDate(requireCostDate(costDate));
        cost.setDescription(normaliseDescription(description));
        return costRepository.save(cost);
    }

    // ==================================================== uthibitisho

    /**
     * Mzunguko wa SHAMBA HILI, au null ikiwa haukutolewa.
     *
     * NULL INARUHUSIWA na ndiyo nusu ya module: gharama isiyo na
     * mzunguko ni gharama ya shamba zima, si ombi lisilokamilika.
     *
     * MZUNGUKO WA SHAMBA JINGINE UNAKATALIWA, na hiyo ndiyo sheria pekee
     * ambayo database haiwezi kuisema yenyewe: `cycles` haina farm_id
     * (shamba lake linafikiwa kupitia `production_units`), hivyo FK ya
     * V23 ingekubali mzunguko wa shamba lolote kwenye gharama ya shamba
     * lolote. Bila ukaguzi huu, gharama ya shamba A ingeweza kupandikizwa
     * kwenye ripoti ya mzunguko wa shamba B.
     *
     * JIBU LILE LILE kwa "haupo" na "si wa shamba hili" kwa makusudi:
     * kutofautisha kungemwambia mwombaji ni mizunguko mingapi ipo kwenye
     * mashamba asiyoyajua. Ni hoja ile ile ya AssetService.
     * requireCallersFarm, ikitekelezwa kwa VALIDATION_ERROR kwa sababu
     * hapa tatizo ni FOMU (mzunguko usiolingana na shamba lililochaguliwa
     * kwenye ombi lile lile), si mamlaka.
     */
    private Cycle requireCycleInFarm(Integer cycleId, Farm farm) {
        if (cycleId == null) {
            return null;
        }

        Cycle cycle = cycleRepository.findByCycleId(cycleId).orElse(null);
        if (cycle == null
                || cycle.getUnit() == null
                || cycle.getUnit().getFarm() == null
                || !farm.getFarmId().equals(cycle.getUnit().getFarm().getFarmId())) {
            throw new IllegalArgumentException(
                    "Mzunguko huu haupo kwenye shamba ulilochagua.");
        }
        return cycle;
    }

    /**
     * Kiasi cha gharama - LAZIMA kiwe zaidi ya sifuri.
     *
     * UKAGUZI NI MARA MBILI, kama AssetService.requireCost, na wa pili
     * ndio wenye maana isiyoonekana: `0.004` ni chanya, lakini kwa
     * NUMERIC(14,2) ni `0.00` - ambayo CHECK ya V23
     * (chk_costs_amount_positive) itaikataa kwenye database, ikirudi kama
     * sentensi ya jumla kuhusu vikwazo badala ya ujumbe unaotaja uga.
     *
     * Kikomo cha juu ni cha SAFU, si cha kibiashara: NUMERIC(14,2)
     * ikizidishwa inatoa `numeric field overflow` ya PostgreSQL.
     */
    private static BigDecimal requireAmount(Double raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Kiasi cha gharama kinahitajika.");
        }
        if (raw.isNaN() || raw.isInfinite()) {
            throw new IllegalArgumentException("Kiasi cha gharama si namba halali.");
        }
        if (raw <= 0) {
            throw new IllegalArgumentException("Kiasi cha gharama lazima kiwe zaidi ya sifuri.");
        }

        BigDecimal amount = BigDecimal.valueOf(raw).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Kiasi cha gharama ni kidogo mno - hakiwezi kuwa chini ya 0.01.");
        }
        if (amount.compareTo(AMOUNT_MAX) > 0) {
            throw new IllegalArgumentException(
                    "Kiasi cha gharama hakiwezi kuzidi " + AMOUNT_MAX.toPlainString() + ".");
        }
        return amount;
    }

    /**
     * Tarehe ya gharama - LAZIMA, na SI YA BAADAYE.
     *
     * Hakuna chaguo-msingi la "leo", kwa hoja ile ile ya
     * AssetService.requireAcquiredDate: bili inarekodiwa siku
     * ilipolipwa, ambayo mara nyingi si siku inayoingizwa kwenye mfumo.
     * "Leo" kimyakimya ingekuwa uongo unaoingia bila mtu kuchagua.
     *
     * Ya nyuma INARUHUSIWA na ndiyo hali ya kawaida kabisa (bili ya Machi
     * inaingizwa Aprili). Ya baadaye inakataliwa: gharama ambayo bado
     * haijatokea si gharama - ni bajeti, na daftari la bajeti si daftari
     * la matumizi. Sheria ile ile ya DailyTaskService na AssetService.
     *
     * "Leo" ni ya EAT (Africa/Nairobi), si ya server: kati ya saa 3
     * usiku na usiku wa manane, UTC tayari iko KESHO kwa mkulima wa
     * Dodoma - tarehe angeiandika ingekataliwa kama "ya baadaye".
     */
    private LocalDate requireCostDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Tarehe ya gharama inahitajika.");
        }

        LocalDate costDate;
        try {
            costDate = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Tarehe si sahihi. Tumia muundo YYYY-MM-DD.");
        }

        LocalDate today = LocalDate.now(reminderProperties.zoneId());
        if (costDate.isAfter(today)) {
            throw new IllegalArgumentException(
                    "Tarehe ya gharama haiwezi kuwa ya baadaye (" + costDate
                            + "). Leo ni " + today + ".");
        }
        return costDate;
    }

    /** Maelezo tupu ni KUTOKUWA na maelezo, si maandishi yasiyo na herufi. */
    private static String normaliseDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String description = raw.trim();
        return description.isEmpty() ? null : description;
    }

    /**
     * Aina ya gharama, ikiwa BADO ipo.
     *
     * HAIKAGULIWI kwa shamba kwa makusudi: `cost_categories` ni katalogi
     * ya kimfumo isiyo na farm_id (V23) - sheria ile ile ya
     * AssetService.requireCategory na CycleService.create kwa speciesId.
     */
    private CostCategory requireCategory(Integer costCategoryId) {
        if (costCategoryId == null) {
            throw new IllegalArgumentException("Aina ya gharama inahitajika.");
        }
        return costCategoryRepository.findByCostCategoryId(costCategoryId)
                .orElseThrow(() -> new IllegalArgumentException("Aina ya gharama haijulikani."));
    }

    /**
     * Jina la aina lililopunguzwa nafasi tupu, likiwa halijachukuliwa.
     *
     * Swali linahesabu hata aina ZILIZOFUTWA - angalia
     * CostCategoryRepository.countByNameIncludingDeleted kwa kwa nini.
     */
    private String requireAvailableCategoryName(String raw) {
        String name = raw == null ? "" : raw.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Jina la aina ya gharama linahitajika.");
        }
        if (name.length() > CATEGORY_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Jina la aina ya gharama lisizidi herufi " + CATEGORY_NAME_MAX_LENGTH + ".");
        }
        if (costCategoryRepository.countByNameIncludingDeleted(name, null) > 0) {
            throw new ConflictException("Aina ya gharama yenye jina hili tayari ipo.");
        }
        return name;
    }
}
