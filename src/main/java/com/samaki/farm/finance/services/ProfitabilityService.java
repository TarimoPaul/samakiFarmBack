package com.samaki.farm.finance.services;

import com.samaki.farm.asset.repository.AssetRepository;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.cost.repository.CostRepository;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.cycle.services.FinanceVisibility;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.services.FarmMembershipService;
import com.samaki.farm.feed.entity.FeedPurchase;
import com.samaki.farm.feed.repository.FeedPurchaseRepository;
import com.samaki.farm.feed.repository.FeedStockMovementRepository;
import com.samaki.farm.finance.dto.CostCategoryAmount;
import com.samaki.farm.finance.dto.CycleProfitability;
import com.samaki.farm.finance.dto.FarmProfitability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FAIDA - mahali PEKEE panapojumlisha mapato na gharama pamoja. Frontend
 * inaonyesha tu.
 *
 * =====================================================================
 * RUHUSA: `view_finance` (OWNER + FARM_MANAGER) kwa uso WOTE - "Kuona
 * gharama/mauzo/faida" ndiyo maana yake tangu V1. Kwa sababu uso mzima
 * umelindwa, hakuna kuficha uga kwa uga hapa (FinanceVisibility): DTO
 * mpya pekee zinarudishwa, si Cycle nzima ambayo resolver zake zingeficha
 * tena bila sababu.
 *
 * SHAMBA: FarmMembershipService.requireCallersFarm - uanachama halisi,
 * kama `costs` na `assets`. Mmiliki wa mashamba mawili anauliza faida ya
 * lolote kati yao; shamba asilo lake ni FORBIDDEN.
 *
 * FORMULA na uanachama wa kipindi: angalia CycleProfitability na
 * FarmProfitability.
 * =====================================================================
 */
@Service
public class ProfitabilityService {

    private static final String PERMISSION = FinanceVisibility.PERMISSION;
    private static final List<String> CLOSED_STATUSES = List.of(Cycle.HARVESTED, Cycle.FAILED);

    private final CycleRepository cycleRepository;
    private final CostRepository costRepository;
    private final FeedPurchaseRepository feedPurchaseRepository;
    private final FeedStockMovementRepository movementRepository;
    private final AssetRepository assetRepository;
    private final FarmMembershipService farmMembership;
    private final PermissionChecker permissionChecker;

    public ProfitabilityService(CycleRepository cycleRepository,
                                CostRepository costRepository,
                                FeedPurchaseRepository feedPurchaseRepository,
                                FeedStockMovementRepository movementRepository,
                                AssetRepository assetRepository,
                                FarmMembershipService farmMembership,
                                PermissionChecker permissionChecker) {
        this.cycleRepository = cycleRepository;
        this.costRepository = costRepository;
        this.feedPurchaseRepository = feedPurchaseRepository;
        this.movementRepository = movementRepository;
        this.assetRepository = assetRepository;
        this.farmMembership = farmMembership;
        this.permissionChecker = permissionChecker;
    }

    /**
     * Faida ya mzunguko mmoja. Unaoendelea SI hitilafu: revenue na
     * cycleNetProfit zinarudi null, status ACTIVE.
     */
    @Transactional(readOnly = true)
    public CycleProfitability cycleProfitability(Integer cycleId) {
        permissionChecker.require(PERMISSION);

        if (cycleId == null) {
            throw new IllegalArgumentException("Kitambulisho cha mzunguko kinahitajika.");
        }
        Cycle cycle = cycleRepository.findByCycleId(cycleId)
                .filter(c -> c.getUnit() != null && c.getUnit().getFarm() != null)
                .orElseThrow(() -> new IllegalArgumentException("Mzunguko haujulikani"));
        farmMembership.requireCallersFarm(cycle.getUnit().getFarm().getFarmId());

        BigDecimal operational = operationalCostByCycle(List.of(cycleId))
                .getOrDefault(cycleId, BigDecimal.ZERO);
        return CycleProfitability.of(cycle, operational);
    }

    @Transactional(readOnly = true)
    public FarmProfitability farmProfitability(Integer farmId, String fromDate, String toDate) {
        permissionChecker.require(PERMISSION);
        Farm farm = farmMembership.requireCallersFarm(farmId);

        LocalDate from = requireDate(fromDate, "Tarehe ya mwanzo");
        LocalDate to = requireDate(toDate, "Tarehe ya mwisho");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Tarehe ya mwanzo (" + from
                    + ") haiwezi kuwa baada ya tarehe ya mwisho (" + to + ").");
        }

        // ---- mizunguko iliyofungwa kipindini, na gharama zinazoifuata ----
        List<Cycle> periodCycles = cycleRepository
                .findByUnit_Farm_FarmIdAndStatusInAndActualHarvestDateBetweenOrderByActualHarvestDateAscCycleIdAsc(
                        farm.getFarmId(), CLOSED_STATUSES, from, to);
        List<Integer> cycleIds = periodCycles.stream().map(Cycle::getCycleId).toList();

        Map<Integer, BigDecimal> operationalByCycle = operationalCostByCycle(cycleIds);
        List<CycleProfitability> cycles = periodCycles.stream()
                .map(cycle -> CycleProfitability.of(cycle,
                        operationalByCycle.getOrDefault(cycle.getCycleId(), BigDecimal.ZERO)))
                .toList();

        BigDecimal farmRevenue = sum(cycles, CycleProfitability::revenue);
        BigDecimal fingerlingCost = sum(cycles, CycleProfitability::fingerlingCost);
        BigDecimal cycleOperationalCost = sum(cycles, CycleProfitability::cycleOperationalCost);
        BigDecimal cycleCosts = fingerlingCost.add(cycleOperationalCost);
        List<CostCategoryAmount> cycleByCategory = cycleIds.isEmpty() ? List.of()
                : costRepository.sumByCategoryForCycles(cycleIds).stream().map(CostCategoryAmount::of).toList();

        // ---- ngazi ya shamba: chakula na gharama za shamba zima ----
        FeedCost feed = feedCost(farm.getFarmId(), from, to);

        List<CostCategoryAmount> farmByCategory = costRepository
                .sumFarmLevelByCategory(farm.getFarmId(), from, to).stream()
                .map(CostCategoryAmount::of)
                .toList();
        BigDecimal farmOperationalCost = sum(farmByCategory, CostCategoryAmount::amount);

        BigDecimal farmCosts = cycleCosts.add(feed.total()).add(farmOperationalCost);

        return new FarmProfitability(
                farm.getFarmId(),
                farm.getName(),
                from,
                to,
                farmRevenue,
                fingerlingCost,
                cycleOperationalCost,
                cycleByCategory,
                cycleCosts,
                feed.total(),
                feed.reversedExcluded(),
                farmOperationalCost,
                farmByCategory,
                farmCosts,
                farmRevenue.subtract(farmCosts),
                cycles.size(),
                (int) cycles.stream().filter(CycleProfitability::incomplete).count(),
                cycles,
                // MTAJI - kando kabisa, haumo kwenye farmCosts wala farmNetProfit.
                assetRepository.sumCostAcquiredBetween(farm.getFarmId(), from, to));
    }

    private record FeedCost(BigDecimal total, int reversedExcluded) {}

    /**
     * Chakula cha kipindi BILA manunuzi yaliyobatilishwa.
     *
     * SUM ya kawaida INGEHESABU MARA MBILI: kubatilisha hakufuti safu ya
     * ununuzi - kunaandika movement ya OUT inayouelekea (angalia
     * FeedService.reverseFeedPurchase), na kurekebisha kunaongeza ununuzi
     * MPYA juu ya ule. Leja ndiyo inayojua kilichobatilishwa.
     */
    private FeedCost feedCost(Integer farmId, LocalDate from, LocalDate to) {
        Set<Integer> reversed = Set.copyOf(movementRepository.findReversedPurchaseIds(farmId));

        BigDecimal total = BigDecimal.ZERO;
        int excluded = 0;
        for (FeedPurchase purchase : feedPurchaseRepository.findByFarm_FarmIdAndPurchaseDateBetween(farmId, from, to)) {
            if (reversed.contains(purchase.getPurchaseId())) {
                excluded++;
            } else if (purchase.getTotalCost() != null) {
                total = total.add(purchase.getTotalCost());
            }
        }
        return new FeedCost(total, excluded);
    }

    /** Orodha tupu haifiki database: `cycle_id IN ()` si SQL halali. */
    private Map<Integer, BigDecimal> operationalCostByCycle(Collection<Integer> cycleIds) {
        if (cycleIds.isEmpty()) {
            return Map.of();
        }
        return costRepository.sumByCycle(cycleIds).stream()
                .collect(Collectors.toMap(CostRepository.CycleAmount::getCycleId,
                        CostRepository.CycleAmount::getAmount));
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> amount) {
        return rows.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate requireDate(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " inahitajika.");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + " si sahihi. Tumia muundo YYYY-MM-DD.");
        }
    }
}
