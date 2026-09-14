package com.samaki.farm.harvest.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.harvest.entity.HarvestEvent;
import com.samaki.farm.harvest.entity.HarvestEvent.Reason;
import com.samaki.farm.harvest.repository.HarvestEventRepository;
import com.samaki.farm.reminder.config.ReminderProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MATUKIO YA MAVUNO - samaki wanaotoka kwenye bwawa, siku kwa siku.
 *
 * =====================================================================
 * Mavuno ni matukio mengi (V25), na closeCycle inayajumlisha. Hivyo
 * matukio haya NDIYO chanzo cha idadi, uzito, vifo, mapato na kiwango
 * cha kuishi cha kila mzunguko - kila sheria hapa inalinda namba hizo.
 *
 * RUHUSA:
 *   * kurekodi / kufuta -> `record_harvest` (V26, OWNER/FARM_MANAGER).
 *   * kusoma            -> `view_dashboard`, kama `cycles`. saleAmount
 *                          inafichwa bila `view_finance` - kwenye
 *                          HarvestResolver, si hapa.
 *
 * Zote ni za SHAMBA TEULE (requireFarmScope), kama CycleService - si
 * daftari la kampuni kama `costs`. Matukio ni kazi ya shambani ya
 * mzunguko fulani, na mzunguko uko kwenye shamba moja.
 *
 * MZUNGUKO ULIOFUNGWA NI WA MWISHO: tukio haliwezi kurekodiwa wala
 * kufutwa baada ya closeCycle (CYCLE_ALREADY_CLOSED). Jumla za kufunga
 * ni picha ya matukio ya siku ile; tukio la baadaye lingekuwepo bila
 * kuwemo kwenye jumla, na kufuta kungebadilisha historia bila jumla
 * kufuata. Hakuna kufungua upya kwenye hatua hii.
 *
 * KUFULI: kila operesheni inayoandika inafungua mzunguko kwa
 * findForUpdateByCycleId - angalia CycleRepository kwa sababu.
 * =====================================================================
 */
@Service
public class HarvestService {

    private static final String RECORD_PERMISSION = "record_harvest";
    private static final String VIEW_PERMISSION = "view_dashboard";

    /** `weight_kg` ni NUMERIC(10,2), `sale_amount` NUMERIC(14,2) (V25). */
    private static final int SCALE = 2;
    private static final BigDecimal WEIGHT_MAX = new BigDecimal("99999999.99");
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("999999999999.99");

    private final HarvestEventRepository harvestEventRepository;
    private final CycleRepository cycleRepository;
    private final PermissionChecker permissionChecker;
    private final ReminderProperties reminderProperties;

    public HarvestService(HarvestEventRepository harvestEventRepository,
                          CycleRepository cycleRepository,
                          PermissionChecker permissionChecker,
                          ReminderProperties reminderProperties) {
        this.harvestEventRepository = harvestEventRepository;
        this.cycleRepository = cycleRepository;
        this.permissionChecker = permissionChecker;
        this.reminderProperties = reminderProperties;
    }

    // ==================================================== kusoma

    /**
     * Matukio ya mzunguko mmoja, mapya kwanza - wa kuendelea NA
     * uliofungwa (historia ya mavuno inabaki inasomeka).
     */
    @Transactional(readOnly = true)
    public List<HarvestEvent> listForCycle(Integer cycleId) {
        permissionChecker.requireFarmScope(VIEW_PERMISSION);

        if (cycleId == null) {
            throw new IllegalArgumentException("Kitambulisho cha mzunguko kinahitajika.");
        }
        Cycle cycle = cycleRepository.findByCycleId(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("Mzunguko haujulikani"));
        permissionChecker.requireResourceInCallersFarm(cycle.getUnit().getFarm().getFarmId());

        return harvestEventRepository.findByCycle_CycleIdOrderByEventDateDescHarvestEventIdDesc(cycleId);
    }

    // ==================================================== kuandika

    @Transactional
    public HarvestEvent record(Integer cycleId, String eventDate, Integer fishCount,
                               Double weightKg, String reason, Double saleAmount) {
        permissionChecker.requireFarmScope(RECORD_PERMISSION);

        Cycle cycle = requireOpenCycleForUpdate(cycleId);
        Reason parsedReason = requireReason(reason);

        HarvestEvent event = new HarvestEvent();
        event.setCycle(cycle);
        event.setReason(parsedReason);
        event.setEventDate(requireEventDate(eventDate, cycle));
        event.setFishCount(requireFishCount(fishCount));
        event.setWeightKg(requireWeight(weightKg, parsedReason));
        event.setSaleAmount(requireSaleAmount(saleAmount, parsedReason));
        return harvestEventRepository.save(event);
    }

    /**
     * SOFT-DELETE ya tukio lililokosewa - kwa mzunguko UNAOENDELEA pekee.
     *
     * Ipo kwa sababu idadi iliyokosewa (500 badala ya 50) ingepotosha
     * kiwango cha kuishi MILELE - tukio halina njia nyingine ya
     * kurekebishwa. Rekebisha = futa + rekodi upya.
     *
     * Soft, si DELETE: safu inabaki (is_deleted, deleted_by) - nani
     * alifuta nini ni swali la halali kwa namba zinazobeba fedha.
     * @SQLRestriction inaliondoa kwenye orodha NA kwenye jumla ya
     * closeCycle.
     */
    @Transactional
    public boolean delete(Integer harvestEventId) {
        permissionChecker.requireFarmScope(RECORD_PERMISSION);

        if (harvestEventId == null) {
            throw new IllegalArgumentException("Kitambulisho cha tukio kinahitajika.");
        }

        // MZUNGUKO KWANZA, TUKIO BAADAYE - na mpangilio ni wa lazima.
        // Tukio lina `cycle` EAGER: likisomwa kwanza, mzunguko ungeingia
        // kwenye persistence context BILA kufuli, na findForUpdateByCycleId
        // ingerudisha instance ile ile ya zamani - hali (`status`) iliyosomwa
        // kabla ya kufuli. Kufunga kulikotokea katikati kusingeonekana.
        Integer cycleId = harvestEventRepository.findCycleIdByHarvestEventId(harvestEventId)
                .orElseThrow(() -> new IllegalArgumentException("Tukio la mavuno halijulikani."));
        requireOpenCycleForUpdate(cycleId);

        HarvestEvent event = harvestEventRepository.findByHarvestEventId(harvestEventId)
                .orElseThrow(() -> new IllegalArgumentException("Tukio la mavuno halijulikani."));
        event.softDelete(permissionChecker.currentUser().getUserId());
        harvestEventRepository.save(event);
        return true;
    }

    /**
     * KUREKEBISHA tukio: kufuta la zamani (soft) na kurekodi jipya, KWENYE
     * TRANSACTION MOJA - mtindo wa FeedService.correctFeedPurchase.
     *
     * Si UPDATE ya safu kwa makusudi: namba hizi zinabeba fedha na kiwango
     * cha kuishi, na safu ya zamani (is_deleted, deleted_by) inabaki kueleza
     * nini kilibadilishwa na nani. Ni mutation MOJA kwa sababu ile ile ya
     * correctFeedPurchase: mteja akiita delete kisha record na ombi la pili
     * likashindwa, tukio lingepotea bila mbadala.
     *
     * Uthibitisho WOTE unafanyika KABLA ya kufuta: tukio jipya likikataliwa,
     * la zamani linabaki kama lilivyo. Mzunguko ni ule ule wa tukio la zamani
     * - kurekebisha hakuhamishi tukio kwenye mzunguko mwingine.
     *
     * Inarudisha tukio JIPYA (kitambulisho kipya).
     */
    @Transactional
    public HarvestEvent correct(Integer harvestEventId, String eventDate, Integer fishCount,
                                Double weightKg, String reason, Double saleAmount) {
        permissionChecker.requireFarmScope(RECORD_PERMISSION);

        if (harvestEventId == null) {
            throw new IllegalArgumentException("Kitambulisho cha tukio kinahitajika.");
        }

        // Mzunguko kwanza, tukio baadaye - sababu ile ile ya delete().
        Integer cycleId = harvestEventRepository.findCycleIdByHarvestEventId(harvestEventId)
                .orElseThrow(() -> new IllegalArgumentException("Tukio la mavuno halijulikani."));
        Cycle cycle = requireOpenCycleForUpdate(cycleId);

        HarvestEvent original = harvestEventRepository.findByHarvestEventId(harvestEventId)
                .orElseThrow(() -> new IllegalArgumentException("Tukio la mavuno halijulikani."));

        Reason parsedReason = requireReason(reason);
        HarvestEvent replacement = new HarvestEvent();
        replacement.setCycle(cycle);
        replacement.setReason(parsedReason);
        replacement.setEventDate(requireEventDate(eventDate, cycle));
        replacement.setFishCount(requireFishCount(fishCount));
        replacement.setWeightKg(requireWeight(weightKg, parsedReason));
        replacement.setSaleAmount(requireSaleAmount(saleAmount, parsedReason));

        original.softDelete(permissionChecker.currentUser().getUserId());
        harvestEventRepository.save(original);
        return harvestEventRepository.save(replacement);
    }

    // ==================================================== uthibitisho

    /**
     * Mzunguko wa shamba la mwombaji, UNAOENDELEA, na mstari wake
     * ukifungwa hadi transaction iishe.
     *
     * Shamba LINAKAGULIWA KABLA ya hali: mzunguko wa shamba jingine ni
     * FORBIDDEN hata ukiwa umefungwa - vinginevyo jibu lingevujisha hali ya
     * mizunguko ya mashamba mengine.
     */
    private Cycle requireOpenCycleForUpdate(Integer cycleId) {
        if (cycleId == null) {
            throw new IllegalArgumentException("Kitambulisho cha mzunguko kinahitajika.");
        }
        Cycle cycle = cycleRepository.findForUpdateByCycleId(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("Mzunguko haujulikani"));
        permissionChecker.requireResourceInCallersFarm(cycle.getUnit().getFarm().getFarmId());

        if (!Cycle.ACTIVE.equals(cycle.getStatus())) {
            throw new ConflictException(
                    "Mzunguko huu umefungwa (" + cycle.getStatus() + ", tarehe "
                            + cycle.getActualHarvestDate() + "). Matukio ya mavuno hayawezi "
                            + "kurekodiwa wala kufutwa baada ya kufunga.",
                    ErrorCodes.CYCLE_ALREADY_CLOSED);
        }
        return cycle;
    }

    /**
     * SOLD / DIED / REMOVED - tatu, hakuna nyingine. Ujumbe unaorodhesha
     * zinazokubalika kutoka kwa enum yenyewe (kama
     * ProductionUnitService.parseType), si jina la darasa la Java.
     */
    private static Reason requireReason(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return Reason.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException unknown) {
                // inaangukia kwenye ujumbe wa pamoja hapa chini
            }
        }
        String allowed = Arrays.stream(Reason.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Sababu ya tukio si sahihi. Chagua: " + allowed + ".");
    }

    /**
     * Tarehe ya tukio: LAZIMA, SI KABLA ya kuweka, SI YA BAADAYE.
     *
     * Kabla ya kuweka: samaki hawawezi kutoka bwawani kabla ya kuingia.
     * Ya baadaye: mauzo ambayo bado hayajatokea ni makubaliano, si mauzo -
     * na yangeingia kwenye jumla ya kufunga kama yametokea. "Leo" ni ya
     * EAT, si ya server (angalia CostService.requireCostDate).
     */
    private LocalDate requireEventDate(String raw, Cycle cycle) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Tarehe ya tukio inahitajika.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Tarehe si sahihi. Tumia muundo YYYY-MM-DD.");
        }
        if (cycle.getStockingDate() != null && date.isBefore(cycle.getStockingDate())) {
            throw new IllegalArgumentException("Tarehe ya tukio (" + date
                    + ") haiwezi kuwa kabla ya tarehe ya kuweka (" + cycle.getStockingDate() + ").");
        }
        LocalDate today = LocalDate.now(reminderProperties.zoneId());
        if (date.isAfter(today)) {
            throw new IllegalArgumentException(
                    "Tarehe ya tukio haiwezi kuwa ya baadaye (" + date + "). Leo ni " + today + ".");
        }
        return date;
    }

    /**
     * Idadi ya samaki: LAZIMA > 0.
     *
     * HAKUNA kikomo cha juu dhidi ya fingerlingsCount KWA MAKUSUDI: kuhesabu
     * samaki si sayansi kamili, na V19 iliruhusu kuishi zaidi ya 1.0 kwa
     * sababu hiyo hiyo. Kukataa kungegeuza kosa la kuhesabu la vifaranga
     * kuwa mauzo yasiyoweza kurekodiwa.
     */
    private static int requireFishCount(Integer raw) {
        if (raw == null || raw <= 0) {
            throw new IllegalArgumentException("Idadi ya samaki lazima iwe zaidi ya sifuri.");
        }
        return raw;
    }

    /**
     * Uzito: LAZIMA kwa SOLD (samaki anauzwa kwa kilo), HIARI kwa
     * DIED/REMOVED (mizoga haipimwi). Ukitolewa, lazima uwe > 0.
     */
    private static BigDecimal requireWeight(Double raw, Reason reason) {
        if (raw == null) {
            if (reason == Reason.SOLD) {
                throw new IllegalArgumentException("Uzito (kg) unahitajika kwa mauzo (SOLD).");
            }
            return null;
        }
        return requirePositive(raw, WEIGHT_MAX, "Uzito (kg)");
    }

    /**
     * Kiasi cha mauzo: LAZIMA > 0 kwa SOLD. Kwa DIED/REMOVED ni NULL KILA
     * MARA - null au 0 kutoka kwa mteja inakubalika (fomu nyingi hutuma
     * sifuri kwa uga tupu), lakini kiasi halisi kinakataliwa badala ya
     * kuhifadhiwa: mzoga hauna mapato, na kiasi kilichohifadhiwa kingeingia
     * kwenye jumla ya mapato.
     */
    private static BigDecimal requireSaleAmount(Double raw, Reason reason) {
        if (reason != Reason.SOLD) {
            if (raw == null || raw == 0) {
                return null;
            }
            throw new IllegalArgumentException(
                    "Kiasi cha mauzo ni kwa tukio la SOLD pekee - si kwa " + reason + ".");
        }
        if (raw == null) {
            throw new IllegalArgumentException("Kiasi cha mauzo kinahitajika kwa mauzo (SOLD).");
        }
        return requirePositive(raw, AMOUNT_MAX, "Kiasi cha mauzo");
    }

    /**
     * Namba chanya inayotoshea safu yake. Ukaguzi mara mbili, kama
     * CostService.requireAmount: 0.004 ni chanya lakini inakuwa 0.00
     * kwenye NUMERIC(x,2), ambayo CHECK ya V25 ingeikataa kwa ujumbe
     * usiotaja uga.
     */
    private static BigDecimal requirePositive(Double raw, BigDecimal max, String label) {
        if (raw.isNaN() || raw.isInfinite() || raw <= 0) {
            throw new IllegalArgumentException(label + " lazima kiwe zaidi ya sifuri.");
        }
        BigDecimal value = BigDecimal.valueOf(raw).setScale(SCALE, RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(label + " ni kidogo mno - hakiwezi kuwa chini ya 0.01.");
        }
        if (value.compareTo(max) > 0) {
            throw new IllegalArgumentException(label + " hakiwezi kuzidi " + max.toPlainString() + ".");
        }
        return value;
    }
}
