package com.samaki.farm.cycle.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.cycle.dto.CreateCycleInput;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.dailytask.entity.DailyTask;
import com.samaki.farm.dailytask.repository.DailyTaskRepository;
import com.samaki.farm.harvest.entity.HarvestEvent;
import com.samaki.farm.harvest.repository.HarvestEventRepository;
import com.samaki.farm.harvest.services.HarvestTotals;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.species.entity.Species;
import com.samaki.farm.species.repository.SpeciesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * FR-3.2 (kukokotoa expected_harvest_date kiotomatiki) + FR-4.1 (kuzalisha
 * daily_tasks kiotomatiki) - tafsiri ya Java ya kile kilichokuwa
 * cycles.routes.js kwenye toleo la Node (sasa halitumiki tena).
 */
@Service
public class CycleService {

    private final CycleRepository cycleRepository;
    private final ProductionUnitRepository unitRepository;
    private final SpeciesRepository speciesRepository;
    private final DailyTaskRepository dailyTaskRepository;
    private final HarvestEventRepository harvestEventRepository;
    private final PermissionChecker permissionChecker;

    public CycleService(CycleRepository cycleRepository, ProductionUnitRepository unitRepository,
                         SpeciesRepository speciesRepository, DailyTaskRepository dailyTaskRepository,
                         HarvestEventRepository harvestEventRepository,
                         PermissionChecker permissionChecker) {
        this.cycleRepository = cycleRepository;
        this.unitRepository = unitRepository;
        this.speciesRepository = speciesRepository;
        this.dailyTaskRepository = dailyTaskRepository;
        this.harvestEventRepository = harvestEventRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * Hali halali za mzunguko - zilezile zilizoandikwa kwenye Cycle.status
     * na kwenye V1__init_schema.sql.
     */
    private static final Set<String> STATUSES = Set.of(Cycle.ACTIVE, Cycle.HARVESTED, Cycle.FAILED);

    /** `cycles.fingerling_cost` ni NUMERIC(14,2) (V25) - kama `costs.amount`. */
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal MONEY_MAX = new BigDecimal("999999999999.99");

    @Transactional(readOnly = true)
    public List<Cycle> listForCurrentFarm(String status) {
        Integer farmId = permissionChecker.requireFarmScope("view_dashboard");
        if (status != null && !status.isBlank()) {
            return cycleRepository.findByUnit_Farm_FarmIdAndStatus(farmId, requireKnownStatus(status));
        }
        return cycleRepository.findByUnit_Farm_FarmId(farmId);
    }

    /**
     * Hali isiyojulikana ILIKUWA inarudisha [] kimyakimya - hivyo "ACITVE"
     * iliyokosewa herufi ilionekana kama "hakuna mizunguko" badala ya
     * "umeuliza kitu kisichokuwepo" (angalia FRONTEND_BACKEND_AUDIT.md,
     * D-12).
     */
    private static String requireKnownStatus(String status) {
        String normalized = status.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Hali ya mzunguko si sahihi. Chagua: "
                    + String.join(", ", new TreeSet<>(STATUSES)) + ".");
        }
        return normalized;
    }

    @Transactional
    public Cycle create(CreateCycleInput input) {
        permissionChecker.requireFarmScope("edit_cycle");

        ProductionUnit unit = unitRepository.findById(input.unitId())
                .orElseThrow(() -> new IllegalArgumentException("Tanki/bwawa halijulikani"));
        // unitId inatoka kwa mteja, hivyo LAZIMA ithibitishwe: bila mstari
        // huu mtu mwenye 'edit_cycle' angeweza kuunda mzunguko ndani ya
        // tanki la shamba lingine (D-1).
        permissionChecker.requireResourceInCallersFarm(unit.getFarm().getFarmId());

        // speciesId HAIKAGULIWI kwa shamba kwa MAKUSUDI: `species` ni
        // katalogi ya kimfumo inayoshirikiwa na mashamba yote (Sato,
        // Kambale...), si data ya shamba fulani - haina farm_id kabisa
        // (angalia V1__init_schema.sql).
        Species species = speciesRepository.findById(input.speciesId())
                .orElseThrow(() -> new IllegalArgumentException("Aina ya samaki haijulikani"));

        LocalDate stockingDate = LocalDate.parse(input.stockingDate());
        BigDecimal targetHarvestAgeMonths = requireGrowthMonths(species);
        int stockingAgeMonths = requireStockingAgeMonths(input.stockingAgeMonths(), targetHarvestAgeMonths);

        // FR-3.2: kukokotoa tarehe ya mavuno kiotomatiki - MUDA ULIOBAKI,
        // si umri wote wa aina (angalia monthsToGrow).
        LocalDate expectedHarvest = expectedHarvestDate(
                stockingDate, monthsToGrow(targetHarvestAgeMonths, stockingAgeMonths));

        Cycle cycle = new Cycle();
        cycle.setUnit(unit);
        cycle.setSpecies(species);
        cycle.setStockingDate(stockingDate);
        cycle.setFingerlingsCount(input.fingerlingsCount());
        cycle.setStockingAgeMonths(stockingAgeMonths);
        cycle.setFingerlingCost(optionalFingerlingCost(input.fingerlingCost()));
        if (input.survivalRateEstimate() != null) {
            cycle.setSurvivalRateEstimate(BigDecimal.valueOf(input.survivalRateEstimate()));
        }
        cycle.setExpectedHarvestDate(expectedHarvest);
        cycle.setStatus(Cycle.ACTIVE);
        cycle = cycleRepository.save(cycle);

        unit.setStatus("ACTIVE");
        unitRepository.save(unit);

        // FR-4.1: kuzalisha kazi za kawaida za kila siku kiotomatiki
        createDefaultTasks(cycle);

        return cycle;
    }

    /**
     * MUDA ULIOBAKI hadi mavuno: umri lengwa KUTOA umri wa kuwekwa.
     *
     * Hii ndiyo hesabu iliyokuwa ikikosekana. Utabiri ulikuwa
     * `stockingDate + growthMonthsAvg` kwa KILA mzunguko - hesabu
     * inayodhania kwamba kila mzunguko unaanza na vifaranga vya siku ya
     * kwanza. Mkulima anayenunua samaki wa miezi 2 wa aina ya miezi 6 ana
     * miezi 4 ya kusubiri, si 6; alikuwa akiambiwa tarehe ya miezi 2
     * baadaye kuliko ukweli, kila mzunguko, bila dalili yoyote.
     *
     * `stockingAgeMonths = 0` inarudisha `growthMonthsAvg` ile ile
     * iliyokuwa ikitumika - ndiyo maana mizunguko ya zamani (na kila
     * mteja asiyetuma uga huu) haibadilishi jibu hata kidogo.
     */
    static BigDecimal monthsToGrow(BigDecimal targetHarvestAgeMonths, int stockingAgeMonths) {
        return targetHarvestAgeMonths.subtract(BigDecimal.valueOf(stockingAgeMonths));
    }

    /**
     * Umri lengwa wa mavuno unatoka kwenye AINA YA SAMAKI
     * (`species.growth_months_avg`), si kwenye namba iliyoandikwa hapa.
     *
     * Hakuna chaguo-msingi la "miezi 6" kwa makusudi: Sato na Kambale
     * hawakui kwa kasi moja, na katalogi ya species ndiyo inayojua tofauti
     * hiyo - ndiyo iliyokuwa chanzo tangu FR-3.2, na inabaki hivyo.
     *
     * Null ilikuwa ikitokeza NullPointerException (yaani INTERNAL_ERROR)
     * ndani ya hesabu; sasa ni ujumbe unaosema aina gani ina tatizo.
     */
    private static BigDecimal requireGrowthMonths(Species species) {
        BigDecimal growthMonths = species.getGrowthMonthsAvg();
        if (growthMonths == null || growthMonths.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Aina '" + species.getName() + "' haina muda wa kukua (growthMonthsAvg), "
                            + "hivyo tarehe ya mavuno haiwezi kukokotolewa.");
        }
        return growthMonths;
    }

    /**
     * Umri wa kuweka: si hasi, na SI ZAIDI ya umri lengwa wa mavuno.
     *
     * Kikwazo cha juu ndicho chenye maana halisi. Samaki aliyewekwa akiwa
     * tayari amefikia (au kupita) umri wa kuvunwa angetoa muda uliobaki wa
     * sifuri au HASI, na `plusMonths(-1)` ingerudisha tarehe ya mavuno
     * ILIYOKWISHA PITA siku ile ile ya kuweka - utabiri usio na maana
     * ukionekana kama utabiri halali. Kinakataliwa hapa, si kinapotumika.
     */
    private static int requireStockingAgeMonths(Integer raw, BigDecimal targetHarvestAgeMonths) {
        int stockingAgeMonths = raw == null ? 0 : raw;

        if (stockingAgeMonths < 0) {
            throw new IllegalArgumentException(
                    "Umri wa kuweka (miezi) hauwezi kuwa pungufu ya sifuri.");
        }
        if (BigDecimal.valueOf(stockingAgeMonths).compareTo(targetHarvestAgeMonths) >= 0) {
            throw new IllegalArgumentException(
                    "Umri wa kuweka (miezi " + stockingAgeMonths + ") lazima uwe chini ya umri wa "
                            + "kuvunwa wa aina hii (miezi " + targetHarvestAgeMonths.stripTrailingZeros()
                                    .toPlainString() + ").");
        }
        return stockingAgeMonths;
    }

    /**
     * Gharama ya vifaranga - HIARI, na ikitolewa LAZIMA iwe > 0.
     *
     * Hiari kwa hoja ile ile ya stockingAgeMonths: mteja wa zamani
     * asiyeituma anaendelea kufanya kazi, na null inamaanisha
     * "haikurekodiwa" - si "vifaranga vilikuwa bure". Sifuri inakataliwa
     * kwa sababu hiyo hiyo: ingesomeka kama bei halisi.
     *
     * Ukaguzi mara mbili, kama CostService.requireAmount: 0.004 ni chanya
     * lakini NUMERIC(14,2) inaigeuza 0.00, ambayo CHECK ya V25 ingeikataa
     * kwa ujumbe usiotaja uga.
     */
    private static BigDecimal optionalFingerlingCost(Double raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isNaN() || raw.isInfinite() || raw <= 0) {
            throw new IllegalArgumentException(
                    "Gharama ya vifaranga lazima iwe zaidi ya sifuri (au iachwe wazi).");
        }
        BigDecimal cost = BigDecimal.valueOf(raw).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (cost.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Gharama ya vifaranga ni ndogo mno - haiwezi kuwa chini ya 0.01.");
        }
        if (cost.compareTo(MONEY_MAX) > 0) {
            throw new IllegalArgumentException(
                    "Gharama ya vifaranga haiwezi kuzidi " + MONEY_MAX.toPlainString() + ".");
        }
        return cost;
    }

    /**
     * FR-3.2 - stockingDate + MIEZI ILIYOBAKI (angalia monthsToGrow).
     *
     * growth_months_avg ni NUMERIC(4,1), yaani nusu-mwezi ni thamani
     * halali. Awali hapa palikuwa na .longValue() ambayo INAKATA sehemu ya
     * desimali: aina ya miezi 6.5 ilikokotolewa kama 6 - wiki mbili
     * mapema, kimyakimya (angalia FRONTEND_BACKEND_AUDIT.md, D-7).
     *
     * Miezi mizima inaongezwa kama miezi (hivyo tarehe ya mwezi
     * inahifadhiwa), na sehemu ya desimali inageuzwa kuwa SIKU za mwezi
     * halisi inamoangukia - si wastani wa siku 30 - ili nusu ya Februari
     * isihesabiwe sawa na nusu ya Julai.
     */
    static LocalDate expectedHarvestDate(LocalDate stockingDate, BigDecimal growthMonthsAvg) {
        long wholeMonths = growthMonthsAvg.longValue();
        LocalDate date = stockingDate.plusMonths(wholeMonths);

        BigDecimal fraction = growthMonthsAvg.subtract(BigDecimal.valueOf(wholeMonths));
        if (fraction.signum() <= 0) {
            return date;
        }

        long extraDays = fraction.multiply(BigDecimal.valueOf(date.lengthOfMonth()))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return date.plusDays(extraDays);
    }

    // ==================================================================
    // Kufunga mzunguko - KWA MKONO, DAIMA
    // ==================================================================

    /**
     * Kufunga mzunguko: kuvunwa au kufa.
     *
     * =================================================================
     * HAKUNA KUJIFUNGA KIOTOMATIKI, na hilo ni chaguo, si upungufu.
     *
     * `expectedHarvestDate` ni UTABIRI. Tarehe hiyo ikifika hakuna
     * kinachobadilika kwenye database: hakuna scheduler inayoipitia,
     * hakuna query inayolinganisha na leo, na hakuna mahali popote
     * panapoandika `status` kwa sababu ya tarehe (ReminderScheduler -
     * @Scheduled pekee ya app - inashughulikia daily_tasks, na haigusi
     * `cycles` hata kidogo). Mfumo unatabiri na kukumbusha; MTU ndiye
     * anayefunga.
     *
     * Sababu ni ya kiuendeshaji, si ya kiufundi: samaki hawavunwi kwa
     * sababu kalenda imesema. Wanavunwa mkulima akiamua - bei ya soko
     * ikipanda, tanki likihitajika, au wiki mbili baadaye kuliko
     * ilivyotabiriwa. Mzunguko unaojifunga wenyewe ungeacha kuzalisha
     * vikumbusho vya kulisha samaki AMBAO BADO WAKO MAJINI, na
     * mwendeshaji angegundua hilo kwa samaki kufa njaa.
     * =================================================================
     *
     * `edit_cycle`, ILE ILE inayoruhusu kuweka - HAKUNA ruhusa mpya.
     * Anayeweza kuanzisha mzunguko ndiye anayeweza kuufunga; kuigawa
     * kungemaanisha shamba lenye mtu wa kuanzisha bila mtu wa kumaliza.
     * (Matukio ya kila siku yana ruhusa yao - `record_harvest`, V26.)
     *
     * =================================================================
     * JUMLA ZINATOKA KWENYE MATUKIO, SI KWA MWOMBAJI (V25)
     *
     * Mavuno ni matukio mengi ya kila siku (HarvestEvent), si namba moja
     * ya siku ya kufunga. Hivyo closeCycle HAIPOKEI idadi wala uzito
     * tena - inajumlisha matukio yaliyopo (angalia HarvestTotals):
     * harvestedCount = SOLD + REMOVED, mortalityCount = DIED,
     * totalWeightKg = uzito wa SOLD + REMOVED, totalRevenue = mauzo.
     *
     * KUFUNGA NI KWA MWISHO: baada ya hapa matukio hayarekodiwi wala
     * kufutwa (CYCLE_ALREADY_CLOSED), hivyo jumla hizi hazibadiliki kamwe.
     * Kufuli ya mstari (findForUpdateByCycleId) inahakikisha hakuna tukio
     * linaloingia kati ya kujumlisha na kufunga.
     * =================================================================
     *
     * KIWANGO CHA KUISHI HAKIPOKELEWI HAPA. Ni mgawanyo wa
     * harvestedCount kwa fingerlingsCount, unaokokotolewa na DATABASE
     * (angalia Cycle.actualSurvivalRate na V19) - na harvestedCount
     * yenyewe ni jumla ya server. survivalRateEstimate ya siku ya kuweka
     * HAIGUSWI: makisio na matokeo ni vitu viwili, na kulinganisha ndiyo
     * maana ya kuvihifadhi vyote.
     */
    @Transactional
    public Cycle closeCycle(Integer cycleId, String outcome, String actualHarvestDate, String notes) {
        permissionChecker.requireFarmScope("edit_cycle");

        Cycle cycle = requireCycleInCallersFarm(cycleId);
        String closingStatus = requireClosingOutcome(outcome);
        requireStillOpen(cycle);

        List<HarvestEvent> events = harvestEventRepository
                .findByCycle_CycleIdOrderByEventDateDescHarvestEventIdDesc(cycle.getCycleId());
        LocalDate harvestDate = requireHarvestDate(actualHarvestDate, cycle, events);
        HarvestTotals totals = HarvestTotals.of(events);
        requireFishHarvested(totals, closingStatus);

        cycle.setStatus(closingStatus);
        cycle.setActualHarvestDate(harvestDate);
        cycle.setHarvestedCount(totals.harvestedCount());
        cycle.setTotalWeightKg(totals.totalWeightKg());
        cycle.setMortalityCount(totals.mortalityCount());
        cycle.setTotalRevenue(totals.totalRevenue());
        cycle.setHarvestNotes(notes == null || notes.isBlank() ? null : notes.trim());

        // saveAndFlush, si save: `actual_survival_rate` inakokotolewa na
        // database, hivyo UPDATE lazima itekelezwe SASA ili @Generated
        // isome thamani mpya kabla jibu halijaundwa. Kwa `save` pekee,
        // UPDATE ingesubiri commit - baada ya method hii kurudi - na jibu
        // lingebeba thamani ya zamani (null).
        cycle = cycleRepository.saveAndFlush(cycle);

        releaseUnitIfIdle(cycle);

        return cycle;
    }

    /**
     * Mzunguko wa shamba la mwombaji - UKIFUNGWA (FOR UPDATE) - au
     * VALIDATION_ERROR.
     *
     * Derived query (si findById), hivyo @SQLRestriction inachuja
     * uliofutwa - mzunguko uliofutwa hauwezi kufungwa. Kufuli: angalia
     * CycleRepository.findForUpdateByCycleId.
     */
    private Cycle requireCycleInCallersFarm(Integer cycleId) {
        if (cycleId == null) {
            throw new IllegalArgumentException("Kitambulisho cha mzunguko kinahitajika.");
        }
        Cycle cycle = cycleRepository.findForUpdateByCycleId(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("Mzunguko haujulikani"));
        permissionChecker.requireResourceInCallersFarm(cycle.getUnit().getFarm().getFarmId());
        return cycle;
    }

    /**
     * Hali ya KUFUNGA pekee: HARVESTED au FAILED.
     *
     * 'ACTIVE' inakataliwa hapa ingawa ni hali halali ya mzunguko:
     * closeCycle ni ya kufunga, na "kufunga kwa kuiacha wazi" si kitu.
     * Kufungua tena mzunguko uliofungwa si operesheni iliyopo - kama
     * ingehitajika, ingekuwa mutation yake yenye sheria zake.
     */
    private static String requireClosingOutcome(String outcome) {
        String normalized = outcome == null ? "" : outcome.trim().toUpperCase();
        if (!Cycle.HARVESTED.equals(normalized) && !Cycle.FAILED.equals(normalized)) {
            throw new IllegalArgumentException("Matokeo ya mzunguko si sahihi. Chagua: "
                    + Cycle.FAILED + ", " + Cycle.HARVESTED + ".");
        }
        return normalized;
    }

    /**
     * Mzunguko uliokwisha fungwa HAUFUNGWI tena.
     *
     * Si ukamilifu wa kinadharia - ni ulinzi wa NAMBA, kama
     * PURCHASE_ALREADY_REVERSED. Ombi la pili (kubofya mara mbili, au
     * skrini isiyopata jibu la kwanza) lingeandika jumla MPYA juu ya
     * zilizokwisha rekodiwa, pamoja na tarehe na maelezo mapya, na
     * `actual_survival_rate` ingefuata. Kufunga kunatokea MARA MOJA.
     */
    private static void requireStillOpen(Cycle cycle) {
        if (!Cycle.ACTIVE.equals(cycle.getStatus())) {
            throw new ConflictException(
                    "Mzunguko huu tayari umefungwa (" + cycle.getStatus() + ", tarehe "
                            + cycle.getActualHarvestDate() + "). Hauwezi kufungwa tena.",
                    ErrorCodes.CYCLE_ALREADY_CLOSED);
        }
    }

    /**
     * Tarehe ya kufunga: inasomeka, SI KABLA ya kuweka, na SI KABLA ya
     * tukio la mwisho la mavuno.
     *
     * Tarehe ya kuvuna iliyotangulia tarehe ya kuweka ingefanya mzunguko
     * uwe na muda hasi - namba ambayo kila ripoti ya urefu wa mzunguko
     * ingeibeba bila kuiona. Ni kosa la kawaida la kalenda ya mteja, si
     * hali adimu.
     *
     * Na mzunguko uliofungwa tarehe 10 wenye mauzo ya tarehe 15 ni hadithi
     * isiyowezekana: samaki waliuzwa kutoka bwawa lililokwisha vunwa.
     * `events` zimepangwa mpya kwanza, hivyo ya kwanza ndiyo ya mwisho.
     */
    private static LocalDate requireHarvestDate(String raw, Cycle cycle, List<HarvestEvent> events) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Tarehe ya mavuno inahitajika.");
        }
        LocalDate harvestDate;
        try {
            harvestDate = LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException badDate) {
            throw new IllegalArgumentException("Tarehe ya mavuno si sahihi (tumia YYYY-MM-DD).");
        }
        if (cycle.getStockingDate() != null && harvestDate.isBefore(cycle.getStockingDate())) {
            throw new IllegalArgumentException("Tarehe ya mavuno (" + harvestDate
                    + ") haiwezi kuwa kabla ya tarehe ya kuweka (" + cycle.getStockingDate() + ").");
        }
        if (!events.isEmpty() && harvestDate.isBefore(events.get(0).getEventDate())) {
            throw new IllegalArgumentException("Tarehe ya kufunga (" + harvestDate
                    + ") haiwezi kuwa kabla ya tukio la mwisho la mavuno ("
                    + events.get(0).getEventDate() + ").");
        }
        return harvestDate;
    }

    /**
     * HARVESTED inahitaji samaki WALIOTOKA WAKIWA HAI (SOLD au REMOVED).
     *
     * Mzunguko wa HARVESTED bila samaki hai hata mmoja si mavuno - ni
     * hasara, na ina hali yake (FAILED). Kuruhusu hizo mbili kumaanisha
     * kitu kimoja kungefanya ripoti ya "mizunguko iliyofanikiwa"
     * isihesabike. Vifo (DIED) peke yake havitoshi, kwa sababu ile ile.
     *
     * Kinyume chake HAKIKATALIWI: FAILED yenye mauzo ni halali kabisa -
     * samaki wengi wamekufa, waliobaki wameuzwa. Ndiyo hali halisi ya
     * shambani, na kuikataa kungepoteza kilo zilizookolewa. FAILED bila
     * tukio lolote pia ni halali: kuishi 0.0, jibu halisi.
     *
     * (Sheria ya zamani ya "uzito > 0 kwa HARVESTED" imeondoka: SOLD
     * inalazimisha uzito kwenye tukio lenyewe, na REMOVED ina uzito wa
     * hiari - samaki hai waliotolewa bila kupimwa bado ni samaki hai.)
     */
    private static void requireFishHarvested(HarvestTotals totals, String closingStatus) {
        if (Cycle.HARVESTED.equals(closingStatus) && totals.harvestedCount() == 0) {
            throw new IllegalArgumentException(
                    "Hakuna samaki waliouzwa wala kutolewa wakiwa hai kwenye mzunguko huu - "
                            + "rekodi matukio ya mavuno kwanza, au tumia matokeo '"
                            + Cycle.FAILED + "'.");
        }
    }

    /**
     * Tanki linarudi IDLE mzunguko wake wa mwisho ukifungwa.
     *
     * `create` inaliweka ACTIVE; bila hatua hii hakuna kinacholiondoa,
     * hivyo kila tanki lililowahi kutumika lingebaki likionekana
     * limekaliwa MILELE na uga wa `status` ungepoteza maana yake.
     *
     * UKAGUZI WA MZUNGUKO MWINGINE ni wa lazima: hakuna kikwazo
     * kinachozuia tanki moja kuwa na mizunguko miwili inayoendelea, na
     * kufunga mmoja HAKUMAANISHI tanki ni tupu. Kuliachia IDLE hapo
     * kungeripoti nafasi isiyokuwepo.
     */
    private void releaseUnitIfIdle(Cycle closed) {
        ProductionUnit unit = closed.getUnit();
        if (unit == null) {
            return;
        }
        boolean stillBusy = cycleRepository
                .findByUnit_Farm_FarmIdAndStatus(unit.getFarm().getFarmId(), Cycle.ACTIVE).stream()
                .anyMatch(other -> other.getUnit() != null
                        && unit.getUnitId().equals(other.getUnit().getUnitId()));
        if (stillBusy) {
            return;
        }
        unit.setStatus("IDLE");
        unitRepository.save(unit);
    }

    private void createDefaultTasks(Cycle cycle) {
        record DefaultTask(String type, LocalTime time) {}
        List<DefaultTask> defaults = List.of(
                new DefaultTask("Kulisha - Asubuhi", LocalTime.of(7, 0)),
                new DefaultTask("Kulisha - Jioni", LocalTime.of(17, 0)),
                new DefaultTask("Kuangalia Maji", LocalTime.of(8, 0))
        );
        for (DefaultTask dt : defaults) {
            DailyTask task = new DailyTask();
            task.setCycle(cycle);
            task.setTaskType(dt.type());
            task.setScheduledTime(dt.time());
            task.setFrequency("DAILY");
            dailyTaskRepository.save(task);
        }
    }
}
