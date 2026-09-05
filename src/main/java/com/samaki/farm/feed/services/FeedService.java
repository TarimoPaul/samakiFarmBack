package com.samaki.farm.feed.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.repository.UserRepository;
import com.samaki.farm.feed.dto.FeedStockBalance;
import com.samaki.farm.feed.dto.FeedSuitability;
import com.samaki.farm.feed.dto.FeedTypesForCycle;
import com.samaki.farm.feed.dto.LogFeedingInput;
import com.samaki.farm.feed.dto.RecordFeedPurchaseInput;
import com.samaki.farm.feed.dto.SuitableFeedType;
import com.samaki.farm.feed.entity.FeedPurchase;
import com.samaki.farm.feed.entity.FeedStockMovement;
import com.samaki.farm.feed.entity.FeedType;
import com.samaki.farm.feed.entity.FeedingLog;
import com.samaki.farm.feed.repository.FeedPurchaseRepository;
import com.samaki.farm.feed.repository.FeedStockMovementRepository;
import com.samaki.farm.feed.repository.FeedTypeRepository;
import com.samaki.farm.feed.repository.FeedingLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module ya chakula. Kanuni kuu: leja ya stoo (feed_stock_movements)
 * HAIANDIKWI na mteja - kila ununuzi unazalisha movement ya IN na kila
 * ulishaji unazalisha movement ya OUT, ndani ya transaction ile ile. Ni
 * mtindo ule ule wa CycleService kuzalisha daily_tasks kiotomatiki.
 *
 * Kanuni ya pili, iliyoongezwa pamoja na katalogi ya FeedType: HESABU YA
 * UMRI NA USALAMA IKO HAPA, si kwenye resolver wala kwenye mteja. Swali
 * "chakula gani kinafaa samaki hawa" lina jibu moja tu sahihi kwa kila
 * mzunguko, na jibu hilo lina athari ya kiusalama (angalia classify).
 * Likikokotolewa mahali pawili, siku moja pataachana.
 */
@Service
public class FeedService {

    /** `feed_types.name` ni VARCHAR(80) (V16). */
    private static final int FEED_TYPE_NAME_MAX_LENGTH = 80;

    private final FeedPurchaseRepository purchaseRepository;
    private final FeedingLogRepository feedingLogRepository;
    private final FeedStockMovementRepository movementRepository;
    private final FeedTypeRepository feedTypeRepository;
    private final FarmRepository farmRepository;
    private final CycleRepository cycleRepository;
    private final UserRepository userRepository;
    private final PermissionChecker permissionChecker;

    public FeedService(FeedPurchaseRepository purchaseRepository, FeedingLogRepository feedingLogRepository,
                        FeedStockMovementRepository movementRepository, FeedTypeRepository feedTypeRepository,
                        FarmRepository farmRepository, CycleRepository cycleRepository,
                        UserRepository userRepository, PermissionChecker permissionChecker) {
        this.purchaseRepository = purchaseRepository;
        this.feedingLogRepository = feedingLogRepository;
        this.movementRepository = movementRepository;
        this.feedTypeRepository = feedTypeRepository;
        this.farmRepository = farmRepository;
        this.cycleRepository = cycleRepository;
        this.userRepository = userRepository;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    public List<FeedPurchase> listPurchases() {
        Integer farmId = permissionChecker.requireFarmScope("view_dashboard");
        return purchaseRepository.findByFarm_FarmIdOrderByPurchaseDateDesc(farmId);
    }

    /** cycleId ikitolewa: ulishaji wa mzunguko mmoja; vinginevyo wa shamba zima. */
    @Transactional(readOnly = true)
    public List<FeedingLog> listFeedingLogs(Integer cycleId) {
        Integer farmId = permissionChecker.requireFarmScope("view_dashboard");
        if (cycleId != null) {
            requireCycleInCallersFarm(cycleId);
            return feedingLogRepository.findByCycle_CycleIdOrderByLogDateDesc(cycleId);
        }
        return feedingLogRepository.findByCycle_Unit_Farm_FarmIdOrderByLogDateDesc(farmId);
    }

    @Transactional(readOnly = true)
    public List<FeedStockMovement> listStockMovements() {
        Integer farmId = permissionChecker.requireFarmScope("view_dashboard");
        return movementRepository.findByFarm_FarmIdOrderByMovedAtDesc(farmId);
    }

    // ==================================================================
    // Katalogi ya aina za chakula (ya KIMFUMO - haina shamba)
    // ==================================================================

    /**
     * Orodha ya katalogi kwa ukurasa wa kuisimamia.
     *
     * `require` pekee, SI `requireFarmScope`: feed_types haina farm_id.
     * Kudai muktadha wa shamba hapa kungezuia usimamizi wa katalogi ya
     * kimfumo kwa sababu isiyohusiana nayo - ni sheria ile ile ya
     * SpeciesService.
     */
    @Transactional(readOnly = true)
    public List<FeedType> listFeedTypes(Boolean activeOnly) {
        permissionChecker.require("manage_feed_stock");
        // Chaguo-msingi ni zinazotumika: ukurasa wa kuchagua chakula
        // hauhitaji zilizozimwa. `activeOnly: false` ndiyo inayoziomba.
        return Boolean.FALSE.equals(activeOnly)
                ? feedTypeRepository.findAllByOrderByNameAsc()
                : feedTypeRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional
    public FeedType createFeedType(String name, Integer minAgeMonths, Integer maxAgeMonths) {
        permissionChecker.require("manage_feed_stock");

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Jina la aina ya chakula linahitajika.");
        }
        int min = requireAgeMonths(minAgeMonths, "Umri wa chini (miezi)");
        int max = requireAgeMonths(maxAgeMonths, "Umri wa juu (miezi)");
        // Dirisha lililopinduka lingefanya classify() irudishe UNSAFE_HIGHER
        // kwa KILA umri - aina isiyofaa samaki yeyote, iliyokaa kimya kwenye
        // katalogi. Inakataliwa hapa, si inapotumika.
        if (max < min) {
            throw new IllegalArgumentException(
                    "Umri wa juu (" + max + ") hauwezi kuwa chini ya umri wa chini (" + min + ").");
        }

        FeedType feedType = new FeedType();
        feedType.setName(requireAvailableName(name, null));
        feedType.setMinAgeMonths(min);
        feedType.setMaxAgeMonths(max);
        feedType.setActive(true);
        return feedTypeRepository.save(feedType);
    }

    /**
     * Kuhariri aina iliyopo: jina na dirisha la umri. HAIGUSI `active`.
     *
     * KUBADILISHA DIRISHA KUNABADILISHA MAAMUZI YA KESHO, si ya jana.
     * Ulishaji uliokwisha rekodiwa unaelekea aina kwa feed_type_id, hivyo
     * historia inabaki ilivyo; kinachobadilika ni jibu la
     * feedTypesForCycle kuanzia sasa - aina iliyokuwa EXACT kwa mzunguko
     * fulani inaweza kuwa SAFE_LOWER au kutoonekana kabisa. Ndiyo maana
     * sheria za dirisha ni ZILE ZILE za createFeedType: kuhariri
     * hakuruhusiwi kuunda dirisha ambalo kusajili kusingeliruhusu.
     */
    @Transactional
    public FeedType updateFeedType(Integer feedTypeId, String name, Integer minAgeMonths, Integer maxAgeMonths) {
        permissionChecker.require("manage_feed_stock");

        FeedType feedType = requireFeedType(feedTypeId);
        int min = requireAgeMonths(minAgeMonths, "Umri wa chini (miezi)");
        int max = requireAgeMonths(maxAgeMonths, "Umri wa juu (miezi)");
        if (max < min) {
            throw new IllegalArgumentException(
                    "Umri wa juu (" + max + ") hauwezi kuwa chini ya umri wa chini (" + min + ").");
        }

        feedType.setName(requireAvailableName(name, feedTypeId));
        feedType.setMinAgeMonths(min);
        feedType.setMaxAgeMonths(max);
        return feedTypeRepository.save(feedType);
    }

    /**
     * Kuzima au kurudisha aina.
     *
     * HII NDIYO NJIA ILIYOKUSUDIWA na V16 kwa aina inayoachwa kutumika:
     * "Aina inayoachwa kutumika haifutwi (rekodi za zamani zinaielekea);
     * inazimwa." Aina iliyozimwa inabaki kwenye katalogi na kwenye kila
     * rekodi ya zamani; kinachoacha ni kuonekana kwenye feedTypesForCycle,
     * yaani hakuna anayeweza kuichagua kwa ulishaji mpya.
     *
     * Idempotent: kuzima iliyokwisha zimwa ni sawa, inarudisha hali ilivyo.
     */
    @Transactional
    public FeedType setFeedTypeActive(Integer feedTypeId, Boolean active) {
        permissionChecker.require("manage_feed_stock");

        if (active == null) {
            throw new IllegalArgumentException("Hali ya 'active' inahitajika.");
        }
        FeedType feedType = requireFeedType(feedTypeId);
        feedType.setActive(active);
        return feedTypeRepository.save(feedType);
    }

    /**
     * Soft-delete ya aina - INAKATALIWA ikiwa bado inatumika popote.
     *
     * Ukaguzi wa zinazoitumia ndio moyo wa method hii, na si tahadhari ya
     * kupita kiasi. Kufuta ni SOFT (BaseEntity.softDelete), hivyo safu ya
     * feed_types inabaki na FK zote tatu zinabaki halali - lakini
     * @SQLRestriction ya FeedType inaificha kwenye kila query. Ulishaji
     * uliokuwa ukiielekea ungebaki ukielekeza mahali pasipoonekana, na
     * `FeedingLog.feedType` ni `FeedType!` kwenye schema: si mstari mmoja
     * ungepotea, ni historia YOTE ya ulishaji ya shamba ingekataa
     * kusomeka. Ni hoja ile ile FARM_IN_USE inayotolewa kwa farm_users.
     *
     * Kikwazo kinapitika: aina isiyowahi kutumiwa - iliyosajiliwa kwa
     * makosa, jina lililoandikwa vibaya - inafutika mara moja. Ujumbe
     * unataja IDADI ya rekodi zinazoizuia na unapendekeza KUIZIMA, ambayo
     * ndiyo hatua sahihi kwa aina iliyowahi kutumika.
     */
    @Transactional
    public void deleteFeedType(Integer feedTypeId) {
        permissionChecker.require("manage_feed_stock");

        FeedType feedType = requireFeedType(feedTypeId);

        long feedings = feedingLogRepository.countByFeedType_FeedTypeId(feedTypeId);
        long purchases = purchaseRepository.countByFeedType_FeedTypeId(feedTypeId);
        long movements = movementRepository.countByFeedType_FeedTypeId(feedTypeId);
        long uses = feedings + purchases + movements;

        if (uses > 0) {
            throw new ConflictException(
                    "Aina hii inatumika kwenye rekodi " + uses
                            + " (ulishaji " + feedings + ", manunuzi " + purchases
                            + ", leja " + movements + "). Haiwezi kufutwa - izime badala yake.",
                    ErrorCodes.FEED_TYPE_IN_USE);
        }

        feedType.softDelete(permissionChecker.currentUser().getUserId());
        feedTypeRepository.save(feedType);
    }

    /**
     * Jina lililopunguzwa nafasi tupu, likiwa halali na halijachukuliwa.
     *
     * Ukaguzi upo hapa - si kwenye bean validation - kwa sababu unahitaji
     * database, na kwa sababu jibu la "limechukuliwa?" linategemea hata
     * aina ZILIZOFUTWA: safu yao ipo, na `feed_types.name` ni UNIQUE.
     * Angalia FeedTypeRepository.countByNameIncludingDeleted.
     */
    private String requireAvailableName(String raw, Integer selfId) {
        String name = raw == null ? "" : raw.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Jina la aina ya chakula linahitajika.");
        }
        if (name.length() > FEED_TYPE_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Jina la aina ya chakula lisizidi herufi " + FEED_TYPE_NAME_MAX_LENGTH + ".");
        }
        if (feedTypeRepository.countByNameIncludingDeleted(name, selfId) > 0) {
            throw new ConflictException("Aina ya chakula yenye jina hili tayari ipo.");
        }
        return name;
    }

    // ==================================================================
    // Salio la stoo - kwa shamba NA kwa aina
    // ==================================================================

    /**
     * Salio la chakula kilichopo stoo (kg), mstari mmoja kwa kila aina
     * iliyowahi kuhamishwa kwenye shamba hili.
     *
     * `view_feed_stock`, SI `view_dashboard` (V17): kiasi cha chakula
     * ghalani ni cha wanaokishika, si cha kila mwenye ruhusa ya kuona
     * ripoti.
     */
    @Transactional(readOnly = true)
    public List<FeedStockBalance> feedStockBalance() {
        Integer farmId = permissionChecker.requireFarmScope("view_feed_stock");

        List<FeedStockMovementRepository.FeedTypeBalanceRow> rows =
                movementRepository.sumBalanceByFarmId(farmId);
        if (rows.isEmpty()) {
            return List.of();
        }

        // Query moja kwa aina ZOTE zilizotokea, si moja kwa kila mstari.
        Map<Integer, FeedType> byId = feedTypeRepository
                .findAllById(rows.stream().map(
                        FeedStockMovementRepository.FeedTypeBalanceRow::getFeedTypeId).toList())
                .stream()
                .collect(Collectors.toMap(FeedType::getFeedTypeId, Function.identity()));

        return rows.stream()
                .map(row -> new FeedStockBalance(byId.get(row.getFeedTypeId()), row.getQuantityKg()))
                // Aina iliyofutwa kwa soft-delete haipo kwenye byId; mstari
                // wake unaachwa badala ya kurudisha feedType tupu, ambayo
                // schema (FeedType!) haingeiruhusu hata hivyo.
                .filter(balance -> balance.feedType() != null)
                // Mpangilio wa herufi: GROUP BY hairudishi mpangilio wowote
                // unaotegemewa, na orodha inayobadilika mpangilio kila
                // ombi ni ngumu kusoma na ngumu kujaribu.
                .sorted(Comparator.comparing(balance -> balance.feedType().getName()))
                .toList();
    }

    // ==================================================================
    // Umri wa mzunguko na chakula kinachofaa
    // ==================================================================

    /**
     * Miezi MIZIMA tangu kuwekwa kwa vifaranga hadi leo, ikishushwa chini,
     * si chini ya sifuri.
     *
     * Kushusha chini (si kuzungusha) kwa makusudi: mzunguko wa siku 59 ni
     * wa MWEZI MMOJA, si miwili. Umri ukikadiriwa juu, samaki wanaweza
     * kupewa chakula chenye punje kubwa kuliko midomo yao - kosa ambalo
     * sheria ya classify() ipo kuliepuka.
     *
     * Sakafu ya sifuri inashughulikia tarehe ya kuweka ya BAADAYE (mzunguko
     * uliopangwa mapema): umri hasi si kitu, na ungefanya kila chakula
     * kionekane UNSAFE_HIGHER.
     */
    public static int cycleAgeMonths(Cycle cycle) {
        LocalDate stockingDate = cycle.getStockingDate();
        if (stockingDate == null) {
            return 0;
        }
        long months = ChronoUnit.MONTHS.between(stockingDate, LocalDate.now());
        return (int) Math.max(0, months);
    }

    /**
     * SHERIA YA MWELEKEO. A ni umri wa mzunguko, [min, max] ni dirisha la
     * aina ya chakula:
     *
     *   min <= A <= max  -> EXACT          (kilichokusudiwa)
     *   max <  A         -> SAFE_LOWER     (cha wadogo; wakubwa wanakila)
     *   min >  A         -> UNSAFE_HIGHER  (cha wakubwa; hawa hawawezi)
     *
     * SI ULINGANIFU. Samaki mkubwa akipewa chakula cha wadogo anakula tu -
     * punje ni ndogo kuliko inavyohitajika, si hatari. Samaki mdogo akipewa
     * cha wakubwa hawezi kukimeza: punje kubwa kuliko mdomo wake ni njaa
     * pale pale chakula kikiwa mbele yake, au kukwama. Ndiyo maana pande
     * mbili zinapewa majibu tofauti badala ya "inafaa/haifai".
     */
    static FeedSuitability classify(FeedType feedType, int ageMonths) {
        if (feedType.getMinAgeMonths() > ageMonths) {
            return FeedSuitability.UNSAFE_HIGHER;
        }
        if (feedType.getMaxAgeMonths() < ageMonths) {
            return FeedSuitability.SAFE_LOWER;
        }
        return FeedSuitability.EXACT;
    }

    /**
     * Chakula kinachofaa mzunguko huu leo.
     *
     * UNSAFE_HIGHER HAIRUDISHWI KABISA - si "inarudishwa ikiwa na onyo".
     * Orodha ya kuchagua ndani ya app ni maelekezo ya kazi: chochote
     * kilichomo kitachaguliwa na mtu fulani siku fulani. Kitu ambacho mfumo
     * tayari unajua hakiwezi kuliwa hakina sababu ya kuwa kwenye orodha.
     *
     * `view_feed_stock` (V17), ile ile ya salio: yote mawili ni maswali ya
     * "nini cha kulisha", na WORKER - ambaye ndiye analisha - anaipata.
     */
    @Transactional(readOnly = true)
    public FeedTypesForCycle feedTypesForCycle(Integer cycleId) {
        permissionChecker.requireFarmScope("view_feed_stock");
        Cycle cycle = requireCycleInCallersFarm(cycleId);

        int ageMonths = cycleAgeMonths(cycle);

        List<SuitableFeedType> suitable = feedTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(feedType -> new SuitableFeedType(feedType, classify(feedType, ageMonths)))
                .filter(entry -> entry.suitability() != FeedSuitability.UNSAFE_HIGHER)
                // EXACT kwanza (enum imepangwa hivyo), kisha jina - hivyo
                // chaguo la kwanza kwenye orodha ndilo lililokusudiwa, na
                // mpangilio hautegemei mpangilio wa kuingizwa kwenye
                // katalogi.
                .sorted(Comparator.comparing(SuitableFeedType::suitability)
                        .thenComparing(entry -> entry.feedType().getName()))
                .toList();

        // Orodha tupu HAPA ina maana moja tu: kila aina inayotumika ni ya
        // samaki wakubwa kuliko hawa (UNSAFE_HIGHER ndiyo iliyochujwa).
        // Katalogi tupu kabisa inatoa jibu lile lile, na kwa mtu anayelisha
        // ni tatizo lile lile: hakuna cha kuwapa.
        return new FeedTypesForCycle(ageMonths, suitable.isEmpty(), suitable);
    }

    // ==================================================================
    // Kuandika
    // ==================================================================

    @Transactional
    public FeedPurchase recordPurchase(RecordFeedPurchaseInput input) {
        Integer farmId = permissionChecker.requireFarmScope("manage_feed_stock");
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new IllegalArgumentException("Farm haipo"));

        BigDecimal quantity = requirePositive(input.quantityKg(), "Kiasi cha chakula");
        FeedType feedType = requireFeedType(input.feedTypeId());

        FeedPurchase purchase = new FeedPurchase();
        purchase.setFarm(farm);
        purchase.setPurchaseDate(LocalDate.parse(input.purchaseDate()));
        purchase.setFeedType(feedType);
        purchase.setQuantityKg(quantity);
        purchase.setUnitCost(requirePositive(input.unitCost(), "Bei ya kilo"));
        purchase.setSupplier(input.supplier());
        purchase = purchaseRepository.save(purchase);

        recordMovement(farm, feedType, FeedStockMovement.Direction.IN, quantity,
                purchase.getPurchaseId(), null);

        return purchase;
    }

    @Transactional
    public FeedingLog logFeeding(LogFeedingInput input) {
        permissionChecker.requireFarmScope("log_feeding");

        Cycle cycle = requireCycleInCallersFarm(input.cycleId());
        BigDecimal quantity = requirePositive(input.quantityKg(), "Kiasi cha chakula");
        FeedType feedType = requireFeedTypeUsableFor(cycle, input.feedTypeId());

        FeedingLog log = new FeedingLog();
        log.setCycle(cycle);
        log.setLogDate(input.logDate() == null ? LocalDate.now() : LocalDate.parse(input.logDate()));
        log.setFeedType(feedType);
        log.setQuantityKg(quantity);
        log.setRecordedBy(currentUser());
        log = feedingLogRepository.save(log);

        recordMovement(cycle.getUnit().getFarm(), feedType, FeedStockMovement.Direction.OUT,
                quantity, null, log.getLogId());

        return log;
    }

    private void recordMovement(Farm farm, FeedType feedType, FeedStockMovement.Direction direction,
                                 BigDecimal quantityKg, Integer purchaseId, Integer feedingLogId) {
        FeedStockMovement movement = new FeedStockMovement();
        movement.setFarm(farm);
        movement.setFeedType(feedType);
        movement.setDirection(direction);
        movement.setQuantityKg(quantityKg);
        movement.setReferencePurchaseId(purchaseId);
        movement.setReferenceFeedingLogId(feedingLogId);
        movementRepository.save(movement);
    }

    /**
     * feeding_logs haina farm_id - shamba lake linajulikana kupitia
     * cycle -> unit -> farm, hivyo scoping lazima ifuate njia hiyo.
     */
    private Cycle requireCycleInCallersFarm(Integer cycleId) {
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("Mzunguko haujulikani"));
        // requireResourceInCallersFarm (si requireSameFarm): hii ni data ya
        // uzalishaji, hivyo inatumia mchekeo ule ule ambao sasa CycleService
        // nayo inautumia - ukaguzi mmoja kwa module zote za shamba.
        permissionChecker.requireResourceInCallersFarm(cycle.getUnit().getFarm().getFarmId());
        return cycle;
    }

    /**
     * HAKUNA ukaguzi wa shamba hapa: katalogi ni ya kimfumo, hivyo aina yoyote
     * inapatikana kwa shamba lolote (kama Species kwenye CycleService.create).
     */
    private FeedType requireFeedType(Integer feedTypeId) {
        if (feedTypeId == null) {
            throw new IllegalArgumentException("Aina ya chakula inahitajika.");
        }
        return feedTypeRepository.findById(feedTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Aina ya chakula haijulikani."));
    }

    /**
     * Lango la kuandika, likitumia HESABU ILE ILE ya classify() ambayo
     * feedTypesForCycle inaitumia kuchuja orodha.
     *
     * KWA NINI LIPO. feedTypesForCycle inaelekeza chaguo, lakini haiwezi
     * kulilazimisha: mteja anaweza kutuma feedTypeId yoyote moja kwa moja -
     * kwa kosa la ukurasa uliochakaa, kwa orodha iliyohifadhiwa kwenye
     * cache, au kwa ombi lililoandikwa kwa mkono. Ushauri unaoweza
     * kupuuzwa kimyakimya si kinga.
     *
     * KINACHOKATALIWA NI KIWILI TU:
     *
     *  - UNSAFE_HIGHER - chakula cha samaki wakubwa kuliko hawa. Punje
     *    kubwa kuliko mdomo ni njaa chakula kikiwa mbele yao.
     *  - Aina iliyozimwa - imeondolewa katalogi kwa sababu fulani, na
     *    kuiendelea kuitumia kungeificha sababu hiyo.
     *
     * KINACHORUHUSIWA, kwa MAKUSUDI, ni SAFE_LOWER: kulisha samaki wakubwa
     * chakula cha wadogo ni uamuzi halali - stoo ya aina sahihi imeisha,
     * au mkulima anamalizia mfuko wa mwisho. Si kosa la kuzuiwa; ni chaguo
     * la kuoneshwa onyo na UI. Kudai EXACT hapa kungegeuza sheria ya
     * mwelekeo kuwa sheria ya ulinganifu - hasa kile ambacho classify()
     * imeundwa kutofautisha.
     */
    private FeedType requireFeedTypeUsableFor(Cycle cycle, Integer feedTypeId) {
        FeedType feedType = requireFeedType(feedTypeId);

        if (!feedType.isActive()) {
            throw new IllegalArgumentException(
                    "Aina ya chakula '" + feedType.getName() + "' haitumiki tena.");
        }

        int ageMonths = cycleAgeMonths(cycle);
        if (classify(feedType, ageMonths) == FeedSuitability.UNSAFE_HIGHER) {
            throw new IllegalArgumentException(
                    "Chakula '" + feedType.getName() + "' ni cha samaki wa miezi "
                            + feedType.getMinAgeMonths() + " kwenda juu; samaki wa mzunguko huu wana miezi "
                            + ageMonths + ". Hawawezi kukila.");
        }

        return feedType;
    }

    private User currentUser() {
        return userRepository.findByUserId(permissionChecker.currentUser().getUserId()).orElse(null);
    }

    /**
     * "Thamani ya X" badala ya "X lazima iwe": ngeli ya jina inatofautiana
     * (kiasi -> kiwe, bei -> iwe), hivyo muundo huu unaepuka kutunga sentensi
     * isiyo sahihi kwa baadhi ya majina.
     */
    private static BigDecimal requirePositive(Double value, String jina) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Thamani ya '" + jina + "' lazima iwe zaidi ya sifuri.");
        }
        return BigDecimal.valueOf(value);
    }

    /**
     * Tofauti na requirePositive: SIFURI INAKUBALIWA. Chakula cha vifaranga
     * ni [0, 0] - mwezi wa kwanza kabisa - hivyo sifuri ni thamani halali
     * hapa, na hasi pekee ndiyo isiyo na maana.
     */
    private static int requireAgeMonths(Integer value, String jina) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("Thamani ya '" + jina + "' haiwezi kuwa pungufu ya sifuri.");
        }
        return value;
    }
}
