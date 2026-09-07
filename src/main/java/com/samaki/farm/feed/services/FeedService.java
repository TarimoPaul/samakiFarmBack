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
import com.samaki.farm.feed.dto.FeedPurchaseView;
import com.samaki.farm.feed.dto.FeedStockBalance;
import com.samaki.farm.feed.dto.FeedSuitability;
import com.samaki.farm.feed.dto.FeedTypeDeactivationImpact;
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
import java.util.Set;
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

    /** `cycles.status`: ACTIVE / HARVESTED / FAILED (angalia Cycle). */
    private static final String ACTIVE_CYCLE_STATUS = Cycle.ACTIVE;

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

    /**
     * Manunuzi ya SHAMBA LA MWOMBAJI, bei ikifichwa kwa asiyeruhusiwa.
     *
     * NGAZI MBILI ZISIZOTEGEMEANA, na ndio jambo zima la method hii:
     *
     *  - `view_dashboard` + muktadha wa shamba -> LANGO. Bila hivyo hakuna
     *    mstari wowote, na mistari inayotoka ni ya shamba lake pekee
     *    (requireFarmScope ndiyo inayotoa farmId - haisomwi popote pengine,
     *    hivyo scoping haiwezi kusahaulika).
     *  - `view_feed_cost` (V18)                -> SAFU MBILI. Bila hiyo
     *    unitCost na totalCost zinarudi null; kiasi, aina, tarehe na
     *    muuzaji zinabaki kama zilivyo.
     *
     * UFICHAJI UPO HAPA, SI KWENYE UI, na tofauti si ya mtindo. Jedwali
     * lisilo na safu ya bei bado lingekuwa limepokea bei ndani ya jibu la
     * JSON: yeyote anayefungua DevTools, anayesoma cache ya mtandao, au
     * anayepiga /graphql moja kwa moja kwa token yake ile ile angeiona.
     * Namba ambayo haipaswi kumfikia mtu HAIPASWI KUONDOKA SERVER.
     *
     * Ukaguzi ni MMOJA kwa ombi, si mmoja kwa mstari: ruhusa haibadiliki
     * katikati ya orodha, na kuiuliza mara elfu kungekuwa kazi bure.
     */
    @Transactional(readOnly = true)
    public List<FeedPurchaseView> listPurchases() {
        Integer farmId = permissionChecker.requireFarmScope("view_dashboard");
        boolean showCost = permissionChecker.has("view_feed_cost");

        // SWALI MOJA kwa orodha nzima, si moja kwa kila mstari - ni sheria ile
        // ile ya ukaguzi wa ruhusa hapo juu.
        Set<Integer> reversedIds = Set.copyOf(movementRepository.findReversedPurchaseIds(farmId));

        return purchaseRepository.findByFarm_FarmIdOrderByPurchaseDateDesc(farmId).stream()
                .map(purchase -> {
                    boolean reversed = reversedIds.contains(purchase.getPurchaseId());
                    return showCost
                            ? FeedPurchaseView.full(purchase, reversed)
                            : FeedPurchaseView.masked(purchase, reversed);
                })
                .toList();
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
     * ATHARI ya kuzima aina hii, KWA SHAMBA LA MWOMBAJI - onyo la kabla ya
     * kubofya, si kikwazo.
     *
     * SETI YA PILI YA SHERIA, si nakala ya setFeedTypeActive. Mutation
     * haiulizi swali lolote kati ya haya mawili kwa makusudi (angalia doc
     * yake): kuzima ni kitendo kinachorudishwa nyuma, hivyo kukikataa kwa
     * sababu ya kilo au samaki kungemfungia msimamizi nje ya katalogi yake.
     * Query hii inatoa TAARIFA ile ile ambayo kikwazo kingekuwa kimeitumia
     * kukataa, na kumwachia mtu uamuzi. Ndiyo maana ni query - HAIANDIKI
     * chochote, na inaweza kuitwa mara ngapi UI inavyotaka.
     *
     * `manage_feed_stock` na SHAMBA: ni ruhusa ile ile ya kitendo
     * kinachofuata (huwezi kupewa onyo la jambo usiloruhusiwa kufanya), na
     * requireFarmScope kwa sababu namba zote mbili ni za shamba - tofauti
     * na listFeedTypes, ambayo inasoma katalogi ya kimfumo na hivyo inatumia
     * `require` pekee.
     *
     * MZUNGUKO TEGEMEZI: uliobaki bila chakula KILICHOKUSUDIWA. Ni mizunguko
     * INAYOENDELEA (ACTIVE) ya shamba hili ambayo:
     *
     *   1. aina hii ni EXACT kwa umri wao wa LEO, NA
     *   2. hakuna aina NYINGINE inayotumika iliyo EXACT wala SAFE_LOWER
     *      kwao.
     *
     * Sharti la pili ndilo linalotofautisha onyo lenye maana na kengele
     * inayolia kila mara: mzunguko wenye chaguo jingine - hata la chakula
     * cha wadogo, ambalo samaki wakubwa wanakila - hauachwi bila kitu, hivyo
     * hauhesabiwi. UNSAFE_HIGHER haiokoi mtu: ni chakula cha samaki wakubwa
     * kuliko hawa, ambacho feedTypesForCycle haikirudishi hata kidogo, hivyo
     * "hesabu kila kisicho UNSAFE_HIGHER" hapa ni swali lile lile
     * feedTypesForCycle inalojiuliza - kwa hesabu ile ile ya classify().
     *
     * Umri unatoka cycleAgeMonths(), si kwa kokotoo lingine: ukikokotolewa
     * mara mbili, siku moja onyo lingesema kitu na skrini ya ulishaji
     * ingeonyesha kingine.
     */
    @Transactional(readOnly = true)
    public FeedTypeDeactivationImpact feedTypeDeactivationImpact(Integer feedTypeId) {
        Integer farmId = permissionChecker.requireFarmScope("manage_feed_stock");
        FeedType feedType = requireFeedType(feedTypeId);

        BigDecimal remainingKg =
                movementRepository.sumBalanceByFarmIdAndFeedTypeId(farmId, feedTypeId);

        // Katalogi mara MOJA, si mara moja kwa kila mzunguko: haibadiliki
        // katikati ya swali moja. Aina yenyewe inaondolewa - swali ni "nini
        // kinabaki ikizimwa", hivyo isingeweza kujihesabia kama mbadala
        // wake mwenyewe (na ingefanya kila mzunguko usiwe tegemezi).
        List<FeedType> alternatives = feedTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(other -> !other.getFeedTypeId().equals(feedType.getFeedTypeId()))
                .toList();

        long dependent = cycleRepository
                .findByUnit_Farm_FarmIdAndStatus(farmId, ACTIVE_CYCLE_STATUS).stream()
                .filter(cycle -> {
                    int ageMonths = cycleAgeMonths(cycle);
                    if (classify(feedType, ageMonths) != FeedSuitability.EXACT) {
                        return false;
                    }
                    return alternatives.stream().noneMatch(
                            other -> classify(other, ageMonths) != FeedSuitability.UNSAFE_HIGHER);
                })
                .count();

        return new FeedTypeDeactivationImpact(remainingKg, (int) dependent);
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

    /**
     * `manage_feed_stock`, BILA KUBADILIKA - `view_feed_cost` (V18)
     * haiingii hapa hata kidogo.
     *
     * Ni ruhusa za maswali tofauti: ya kwanza ni "unaruhusiwa kununua?",
     * ya pili ni "unaruhusiwa kuona bei ya ununuzi wa MWENZAKO?". Mwenye
     * kununua ndiye ALIYEANDIKA unitCost kwenye input hii, na totalCost ni
     * kuzidisha kwake mwenyewe - hivyo jibu la mutation halina namba
     * ambayo mwombaji hakuwa nayo tayari, na halifichwi. Kuidai
     * `view_feed_cost` hapa kungemzuia mnunuzi kurekodi bei anayoilipa.
     *
     * totalCost HAIWEKWI hapa kwa makusudi: ni GENERATED ALWAYS ya
     * database (V1), na FeedPurchase.totalCost ni insertable=false. Hakuna
     * njia ya mteja - wala ya service - kuipandikiza thamani nyingine.
     */
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

    /**
     * Kubatilisha ununuzi kwa REKODI YA KUREKEBISHA - si kwa kuufuta.
     *
     * KWA NINI SI KUFUTA. Ununuzi hauishii kwenye safu yake: unazalisha
     * movement ya IN, na jumla ya movement ndiyo salio la stoo ambalo skrini
     * ya Malisho inaonyesha. Kufuta safu (hata kwa soft-delete) kungeacha
     * salio likidai kilo ambazo hazikuwahi kununuliwa - au, ukifuta movement
     * pia, lingebadilika bila alama yoyote ya KWA NINI. Leja ni ya
     * kuongezwa tu; doc ya FeedStockMovement inasema hivyo.
     *
     * Badala yake inaandikwa movement ya OUT ya kilo ZILE ZILE, ikielekea
     * ununuzi huo. Salio linarudi lilipokuwa, na historia inaeleza mambo
     * mawili badala ya kuficha moja: ununuzi ulitokea, kisha ukabatilishwa.
     *
     * MARA MOJA TU. Kubatilisha mara mbili kungeondoa kilo mara mbili kwa
     * ununuzi uliotokea mara moja - ndiyo maana ya PURCHASE_ALREADY_REVERSED,
     * na ndiyo maana kubofya mara mbili si gharama.
     */
    @Transactional
    public FeedPurchase reverseFeedPurchase(Integer purchaseId) {
        permissionChecker.requireFarmScope("manage_feed_stock");

        FeedPurchase purchase = requirePurchaseInCallersFarm(purchaseId);
        requireNotReversed(purchase);

        recordMovement(purchase.getFarm(), purchase.getFeedType(),
                FeedStockMovement.Direction.OUT, purchase.getQuantityKg(),
                purchase.getPurchaseId(), null);

        return purchase;
    }

    /**
     * Kurekebisha ununuzi: kubatilisha wa zamani na kurekodi mpya, KWENYE
     * TRANSACTION MOJA.
     *
     * NI MUTATION MOJA KWA MAKUSUDI, na si urahisi wa API. Mteja angeweza
     * kuita reverseFeedPurchase kisha recordFeedPurchase, lakini ombi la pili
     * likishindwa - mtandao ukikatika, kikao kikiisha - shamba lingebaki
     * limepoteza kilo zake bila ununuzi wa kuzirudisha, na hakuna skrini
     * ingeeleza kwa nini. Zikiwa ndani ya transaction moja, hali hiyo
     * haiwezekani: yote yanapita au hakuna linalopita.
     *
     * Inarudisha ununuzi MPYA - ndio ulio hai sasa. Wa zamani unabaki
     * kwenye orodha ukiwa umebatilishwa, kwa sababu ndiyo maana ya leja
     * isiyofutika: kilichoandikwa kimebaki kikiandikwa.
     */
    @Transactional
    public FeedPurchase correctFeedPurchase(Integer purchaseId, RecordFeedPurchaseInput input) {
        permissionChecker.requireFarmScope("manage_feed_stock");

        FeedPurchase original = requirePurchaseInCallersFarm(purchaseId);
        requireNotReversed(original);

        recordMovement(original.getFarm(), original.getFeedType(),
                FeedStockMovement.Direction.OUT, original.getQuantityKg(),
                original.getPurchaseId(), null);

        return recordPurchase(input);
    }

    /** Ununuzi wa shamba la mwombaji, au VALIDATION_ERROR. */
    private FeedPurchase requirePurchaseInCallersFarm(Integer purchaseId) {
        if (purchaseId == null) {
            throw new IllegalArgumentException("Kitambulisho cha ununuzi kinahitajika.");
        }
        // findById haitumii @SQLRestriction (angalia BaseEntity), hivyo
        // ukaguzi wa isDeleted ni wa lazima hapa kama ilivyo kwa FeedType.
        FeedPurchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new IllegalArgumentException("Ununuzi haupo."));
        if (purchase.isDeleted()) {
            throw new IllegalArgumentException("Ununuzi haupo.");
        }
        permissionChecker.requireResourceInCallersFarm(purchase.getFarm().getFarmId());
        return purchase;
    }

    private void requireNotReversed(FeedPurchase purchase) {
        boolean alreadyReversed = movementRepository.existsByReferencePurchaseIdAndDirection(
                purchase.getPurchaseId(), FeedStockMovement.Direction.OUT);
        if (alreadyReversed) {
            throw new ConflictException(
                    "Ununuzi huu tayari umebatilishwa. Hauwezi kubatilishwa wala kurekebishwa tena.",
                    ErrorCodes.PURCHASE_ALREADY_REVERSED);
        }
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
