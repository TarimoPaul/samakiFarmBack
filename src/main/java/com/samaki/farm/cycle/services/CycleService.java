package com.samaki.farm.cycle.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.cycle.dto.CreateCycleInput;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.dailytask.entity.DailyTask;
import com.samaki.farm.dailytask.repository.DailyTaskRepository;
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
    private final PermissionChecker permissionChecker;

    public CycleService(CycleRepository cycleRepository, ProductionUnitRepository unitRepository,
                         SpeciesRepository speciesRepository, DailyTaskRepository dailyTaskRepository,
                         PermissionChecker permissionChecker) {
        this.cycleRepository = cycleRepository;
        this.unitRepository = unitRepository;
        this.speciesRepository = speciesRepository;
        this.dailyTaskRepository = dailyTaskRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * Hali halali za mzunguko - zilezile zilizoandikwa kwenye Cycle.status
     * na kwenye V1__init_schema.sql.
     */
    private static final Set<String> STATUSES = Set.of(Cycle.ACTIVE, Cycle.HARVESTED, Cycle.FAILED);

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
     *
     * KIWANGO CHA KUISHI HAKIPOKELEWI HAPA. Ni mgawanyo wa
     * harvestedCount kwa fingerlingsCount, unaokokotolewa na DATABASE
     * (angalia Cycle.actualSurvivalRate na V19) - hivyo hakuna namba ya
     * mwombaji inayoweza kupingana na hesabu. survivalRateEstimate ya siku
     * ya kuweka HAIGUSWI: makisio na matokeo ni vitu viwili, na
     * kulinganisha ndiyo maana ya kuvihifadhi vyote.
     */
    @Transactional
    public Cycle closeCycle(Integer cycleId, String outcome, String actualHarvestDate,
                             Integer harvestedCount, Double totalWeightKg, String notes) {
        permissionChecker.requireFarmScope("edit_cycle");

        Cycle cycle = requireCycleInCallersFarm(cycleId);
        String closingStatus = requireClosingOutcome(outcome);
        requireStillOpen(cycle);

        LocalDate harvestDate = requireHarvestDate(actualHarvestDate, cycle);
        int count = requireHarvestCount(harvestedCount, closingStatus);
        BigDecimal weight = requireHarvestWeight(totalWeightKg, closingStatus);

        cycle.setStatus(closingStatus);
        cycle.setActualHarvestDate(harvestDate);
        cycle.setHarvestedCount(count);
        cycle.setTotalWeightKg(weight);
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
     * Mzunguko wa shamba la mwombaji, au VALIDATION_ERROR.
     *
     * findById HAITUMII @SQLRestriction (angalia BaseEntity), hivyo
     * ukaguzi wa isDeleted ni wa lazima hapa - vinginevyo mzunguko
     * uliofutwa ungeweza kufungwa.
     */
    private Cycle requireCycleInCallersFarm(Integer cycleId) {
        if (cycleId == null) {
            throw new IllegalArgumentException("Kitambulisho cha mzunguko kinahitajika.");
        }
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("Mzunguko haujulikani"));
        if (cycle.isDeleted()) {
            throw new IllegalArgumentException("Mzunguko haujulikani");
        }
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
     * skrini isiyopata jibu la kwanza) lingeandika mavuno MENGINE juu ya
     * yaliyokwisha rekodiwa: idadi ingebadilika, na
     * `actual_survival_rate` - inayokokotolewa kutoka kwake -
     * ingebadilika nayo, kimyakimya. Mavuno yanatokea MARA MOJA.
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
     * Tarehe ya mavuno: inasomeka, na SI KABLA ya kuweka.
     *
     * Tarehe ya kuvuna iliyotangulia tarehe ya kuweka ingefanya mzunguko
     * uwe na muda hasi - namba ambayo kila ripoti ya urefu wa mzunguko
     * ingeibeba bila kuiona. Ni kosa la kawaida la kalenda ya mteja, si
     * hali adimu.
     */
    private static LocalDate requireHarvestDate(String raw, Cycle cycle) {
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
        return harvestDate;
    }

    /**
     * Idadi iliyovunwa. SIFURI INARUHUSIWA kwa FAILED pekee.
     *
     * Mzunguko wa HARVESTED wenye samaki sifuri si mavuno - ni hasara,
     * na ina hali yake (FAILED). Kuruhusu hizo mbili kumaanisha kitu
     * kimoja kungefanya ripoti ya "mizunguko iliyofanikiwa" isihesabike.
     *
     * Kinyume chake HAKIKATALIWI: FAILED yenye idadi ZAIDI ya sifuri ni
     * halali kabisa - samaki wengi wamekufa, waliobaki wamevunwa. Ndiyo
     * hali halisi ya shambani, na kuilazimisha kuwa sifuri kungepoteza
     * kilo zilizookolewa.
     */
    private static int requireHarvestCount(Integer harvestedCount, String closingStatus) {
        if (harvestedCount == null || harvestedCount < 0) {
            throw new IllegalArgumentException(
                    "Idadi ya samaki waliovunwa haiwezi kuwa pungufu ya sifuri.");
        }
        if (Cycle.HARVESTED.equals(closingStatus) && harvestedCount == 0) {
            throw new IllegalArgumentException(
                    "Mavuno ya samaki sifuri si mavuno. Tumia matokeo '" + Cycle.FAILED + "'.");
        }
        return harvestedCount;
    }

    /** Uzito: sheria ile ile ya idadi, kwa sababu ile ile. */
    private static BigDecimal requireHarvestWeight(Double totalWeightKg, String closingStatus) {
        if (totalWeightKg == null || totalWeightKg < 0) {
            throw new IllegalArgumentException(
                    "Uzito wa mavuno (kg) hauwezi kuwa pungufu ya sifuri.");
        }
        if (Cycle.HARVESTED.equals(closingStatus) && totalWeightKg == 0) {
            throw new IllegalArgumentException(
                    "Mavuno ya kilo sifuri si mavuno. Tumia matokeo '" + Cycle.FAILED + "'.");
        }
        return BigDecimal.valueOf(totalWeightKg);
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
