package com.samaki.farm.waterquality.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.repository.UserRepository;
import com.samaki.farm.waterquality.dto.LogWaterQualityInput;
import com.samaki.farm.waterquality.entity.WaterQualityLog;
import com.samaki.farm.waterquality.repository.WaterQualityLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Module ya ubora wa maji.
 *
 * KANUNI KUU, na ndiyo inayoifanya module hii tofauti na nyingine zote:
 * KIPIMO KIBAYA SI KOSA. Oksijeni ya 0.8 mg/L, amonia kubwa, pH ya 4.2 -
 * hizi ndizo sababu hasa mkulima anapima maji. Kuzikataa kungemzuia
 * kurekodi tatizo analotaka kuripoti, na kumfundisha kwamba mfumo
 * unakubali tu vipimo vizuri.
 *
 * Kinachokataliwa ni kile KISICHOWEZEKANA KIMUUNDO: pH nje ya 0-14
 * (mizani yenyewe haiendi huko), oksijeni hasi (si kipimo), na thamani
 * kubwa kuliko safu ya database inavyoweza kubeba. Hakuna ukomo wa
 * "kiafya" popote hapa.
 *
 * SCOPING inapitia PermissionChecker ile ile ya module nyingine -
 * requireFarmScope kwa ruhusa+shamba, na requireResourceInCallersFarm kwa
 * kitengo/mzunguko ulioombwa. HAKUNA ukaguzi wa shamba ulioandikwa hapa
 * kwa mkono: ndiyo hasa hitilafu D-1 iliyokuwa CycleService.
 */
@Service
public class WaterQualityService {

    /** Ruhusa ya kuandika. Kusoma ni view_dashboard, kama module ya chakula. */
    private static final String WRITE_PERMISSION = "log_water_quality";
    private static final String READ_PERMISSION = "view_dashboard";

    /** numeric(4,1): tarakimu nne, moja ikiwa ya desimali. */
    private static final BigDecimal NUMERIC_4_1_MAX = new BigDecimal("999.9");

    private final WaterQualityLogRepository logRepository;
    private final ProductionUnitRepository unitRepository;
    private final CycleRepository cycleRepository;
    private final UserRepository userRepository;
    private final PermissionChecker permissionChecker;

    public WaterQualityService(WaterQualityLogRepository logRepository,
                                ProductionUnitRepository unitRepository,
                                CycleRepository cycleRepository,
                                UserRepository userRepository,
                                PermissionChecker permissionChecker) {
        this.logRepository = logRepository;
        this.unitRepository = unitRepository;
        this.cycleRepository = cycleRepository;
        this.userRepository = userRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * Vipimo, vikichujwa kwa kitengo au kwa mzunguko; bila hoja yoyote ni
     * vya shamba zima.
     *
     * cycleId inaruhusiwa ingawa jedwali halina cycle_id: mzunguko unajua
     * kitengo chake, hivyo "vipimo vya mzunguko huu" ina maana ya "vipimo
     * vya tanki analomoendeshea mzunguko". Hii ni tafsiri, si safu mpya -
     * na inajumuisha vipimo vilivyochukuliwa kabla mzunguko haujaanza,
     * ambavyo ndivyo mkulima anavyotarajia kuona.
     */
    @Transactional(readOnly = true)
    public List<WaterQualityLog> list(Integer unitId, Integer cycleId) {
        Integer farmId = permissionChecker.requireFarmScope(READ_PERMISSION);

        if (cycleId != null) {
            Cycle cycle = requireCycleInCallersFarm(cycleId);
            return logRepository.findByUnit_UnitIdOrderByLogDateDescLogIdDesc(
                    cycle.getUnit().getUnitId());
        }
        if (unitId != null) {
            ProductionUnit unit = requireUnitInCallersFarm(unitId);
            return logRepository.findByUnit_UnitIdOrderByLogDateDescLogIdDesc(unit.getUnitId());
        }
        return logRepository.findByUnit_Farm_FarmIdOrderByLogDateDescLogIdDesc(farmId);
    }

    @Transactional
    public WaterQualityLog log(LogWaterQualityInput input) {
        permissionChecker.requireFarmScope(WRITE_PERMISSION);

        if (input.unitId() == null) {
            throw new IllegalArgumentException("unitId inahitajika.");
        }
        ProductionUnit unit = requireUnitInCallersFarm(input.unitId());

        WaterQualityLog log = new WaterQualityLog();
        log.setUnit(unit);
        log.setLogDate(parseDate(input.logDate()));
        log.setPh(ph(input.ph()));
        log.setTemperature(measurement(input.temperature(), "Joto la maji", true));
        log.setOxygen(measurement(input.oxygen(), "Oksijeni", false));
        log.setNotes(input.notes());
        log.setRecordedBy(currentUser());

        return logRepository.save(log);
    }

    /**
     * Kitengo cha mwombaji. requireResourceInCallersFarm (si requireSameFarm):
     * hii ni data ya uzalishaji, hivyo ruhusa ya kampuni nzima HAIFUNGUI
     * shamba lingine - angalia PermissionChecker.
     */
    private ProductionUnit requireUnitInCallersFarm(Integer unitId) {
        ProductionUnit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Kitengo hakijulikani"));
        permissionChecker.requireResourceInCallersFarm(unit.getFarm().getFarmId());
        return unit;
    }

    private Cycle requireCycleInCallersFarm(Integer cycleId) {
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("Mzunguko haujulikani"));
        permissionChecker.requireResourceInCallersFarm(cycle.getUnit().getFarm().getFarmId());
        return cycle;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Tarehe si sahihi. Tumia muundo YYYY-MM-DD.");
        }
    }

    /**
     * pH: 0-14 ndiyo mizani YENYEWE, si ukomo wa kiafya.
     *
     * pH 4.2 ni maji yenye tindikali kupita kiasi kwa samaki - na
     * INAKUBALIWA, kwa sababu hilo ndilo tatizo linalotakiwa kurekodiwa.
     * pH 15 haipo kwenye mizani hata kidogo, na safu ya numeric(3,1)
     * isingeweza kuitofautisha na kosa la kuandika.
     */
    private BigDecimal ph(Double value) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > 14) {
            throw new IllegalArgumentException("pH lazima iwe kati ya 0 na 14.");
        }
        return BigDecimal.valueOf(value);
    }

    /**
     * Vipimo vya numeric(4,1).
     *
     * `allowNegative` ni ya joto pekee: maji baridi kuliko sifuri ni
     * kipimo halali kutokea, ilhali oksijeni hasi si kipimo bali kosa.
     * Ukomo wa 999.9 ni uwezo wa safu, si maoni kuhusu thamani nzuri -
     * bila hiyo, thamani kubwa ingerudi kama INTERNAL_ERROR ya database
     * badala ya jibu linaloeleweka.
     */
    private BigDecimal measurement(Double value, String jina, boolean allowNegative) {
        if (value == null) {
            return null;
        }
        if (!allowNegative && value < 0) {
            throw new IllegalArgumentException("Thamani ya '" + jina + "' haiwezi kuwa hasi.");
        }
        BigDecimal decimal = BigDecimal.valueOf(value);
        if (decimal.abs().compareTo(NUMERIC_4_1_MAX) > 0) {
            throw new IllegalArgumentException(
                    "Thamani ya '" + jina + "' ni kubwa kuliko kipimo kinavyoweza kuhifadhiwa.");
        }
        return decimal;
    }

    private User currentUser() {
        return userRepository.findByUserId(permissionChecker.currentUser().getUserId()).orElse(null);
    }
}
