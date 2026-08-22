package com.samaki.farm.feed.services;

import com.samaki.farm.auth.security.AuthenticatedUser;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.repository.UserRepository;
import com.samaki.farm.feed.dto.LogFeedingInput;
import com.samaki.farm.feed.dto.RecordFeedPurchaseInput;
import com.samaki.farm.feed.entity.FeedPurchase;
import com.samaki.farm.feed.entity.FeedStockMovement;
import com.samaki.farm.feed.entity.FeedingLog;
import com.samaki.farm.feed.repository.FeedPurchaseRepository;
import com.samaki.farm.feed.repository.FeedStockMovementRepository;
import com.samaki.farm.feed.repository.FeedingLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Module ya chakula. Kanuni kuu: leja ya stoo (feed_stock_movements)
 * HAIANDIKWI na mteja - kila ununuzi unazalisha movement ya IN na kila
 * ulishaji unazalisha movement ya OUT, ndani ya transaction ile ile. Ni
 * mtindo ule ule wa CycleService kuzalisha daily_tasks kiotomatiki.
 */
@Service
public class FeedService {

    private final FeedPurchaseRepository purchaseRepository;
    private final FeedingLogRepository feedingLogRepository;
    private final FeedStockMovementRepository movementRepository;
    private final FarmRepository farmRepository;
    private final CycleRepository cycleRepository;
    private final UserRepository userRepository;
    private final PermissionChecker permissionChecker;

    public FeedService(FeedPurchaseRepository purchaseRepository, FeedingLogRepository feedingLogRepository,
                        FeedStockMovementRepository movementRepository, FarmRepository farmRepository,
                        CycleRepository cycleRepository, UserRepository userRepository,
                        PermissionChecker permissionChecker) {
        this.purchaseRepository = purchaseRepository;
        this.feedingLogRepository = feedingLogRepository;
        this.movementRepository = movementRepository;
        this.farmRepository = farmRepository;
        this.cycleRepository = cycleRepository;
        this.userRepository = userRepository;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    public List<FeedPurchase> listPurchases() {
        AuthenticatedUser user = permissionChecker.require("view_dashboard");
        return purchaseRepository.findByFarm_FarmIdOrderByPurchaseDateDesc(user.getFarmId());
    }

    /** cycleId ikitolewa: ulishaji wa mzunguko mmoja; vinginevyo wa shamba zima. */
    @Transactional(readOnly = true)
    public List<FeedingLog> listFeedingLogs(Integer cycleId) {
        AuthenticatedUser user = permissionChecker.require("view_dashboard");
        if (cycleId != null) {
            requireCycleInCallersFarm(cycleId);
            return feedingLogRepository.findByCycle_CycleIdOrderByLogDateDesc(cycleId);
        }
        return feedingLogRepository.findByCycle_Unit_Farm_FarmIdOrderByLogDateDesc(user.getFarmId());
    }

    @Transactional(readOnly = true)
    public List<FeedStockMovement> listStockMovements() {
        AuthenticatedUser user = permissionChecker.require("view_dashboard");
        return movementRepository.findByFarm_FarmIdOrderByMovedAtDesc(user.getFarmId());
    }

    /** Salio la chakula kilichopo stoo (kg). */
    @Transactional(readOnly = true)
    public BigDecimal feedStockBalance() {
        AuthenticatedUser user = permissionChecker.require("view_dashboard");
        return movementRepository.sumBalanceByFarmId(user.getFarmId());
    }

    @Transactional
    public FeedPurchase recordPurchase(RecordFeedPurchaseInput input) {
        AuthenticatedUser user = permissionChecker.require("manage_feed_stock");
        Farm farm = farmRepository.findById(user.getFarmId())
                .orElseThrow(() -> new IllegalArgumentException("Farm haipo"));

        BigDecimal quantity = requirePositive(input.quantityKg(), "Kiasi cha chakula");

        FeedPurchase purchase = new FeedPurchase();
        purchase.setFarm(farm);
        purchase.setPurchaseDate(LocalDate.parse(input.purchaseDate()));
        purchase.setFeedType(input.feedType());
        purchase.setQuantityKg(quantity);
        purchase.setUnitCost(requirePositive(input.unitCost(), "Bei ya kilo"));
        purchase.setSupplier(input.supplier());
        purchase = purchaseRepository.save(purchase);

        recordMovement(farm, FeedStockMovement.Direction.IN, quantity, purchase.getPurchaseId(), null);

        return purchase;
    }

    @Transactional
    public FeedingLog logFeeding(LogFeedingInput input) {
        permissionChecker.require("log_feeding");

        Cycle cycle = requireCycleInCallersFarm(input.cycleId());
        BigDecimal quantity = requirePositive(input.quantityKg(), "Kiasi cha chakula");

        FeedingLog log = new FeedingLog();
        log.setCycle(cycle);
        log.setLogDate(input.logDate() == null ? LocalDate.now() : LocalDate.parse(input.logDate()));
        log.setFeedType(input.feedType());
        log.setQuantityKg(quantity);
        log.setRecordedBy(currentUser());
        log = feedingLogRepository.save(log);

        recordMovement(cycle.getUnit().getFarm(), FeedStockMovement.Direction.OUT,
                quantity, null, log.getLogId());

        return log;
    }

    private void recordMovement(Farm farm, FeedStockMovement.Direction direction, BigDecimal quantityKg,
                                 Integer purchaseId, Integer feedingLogId) {
        FeedStockMovement movement = new FeedStockMovement();
        movement.setFarm(farm);
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
        permissionChecker.requireSameFarm(cycle.getUnit().getFarm().getFarmId());
        return cycle;
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
}
