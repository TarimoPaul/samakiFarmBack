package com.samaki.farm.dev;

import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.feed.entity.FeedStockMovement;
import com.samaki.farm.feed.entity.FeedType;
import com.samaki.farm.feed.repository.FeedStockMovementRepository;
import com.samaki.farm.feed.repository.FeedTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Katalogi ya chakula na stoo ya kuanzia kwa ONYESHO (demo), kwa PROFILE
 * YA `dev` PEKEE - sawa kabisa na DevSeedService, na kwa sababu ile ile.
 *
 * =====================================================================
 * KWA NINI NI DARASA LAKE, SI SEHEMU YA DevSeedService
 *
 * DevSeedService inatumiwa na vitu VIWILI: app inayoendeshwa kwa dev, NA
 * harness ya majaribio (IntegrationTest.resetAndReseedSchema inaiita moja
 * kwa moja ili kupata mashamba, watumiaji, kitengo na mzunguko).
 *
 * Wahusika na mashamba ni MUKTADHA - majaribio yanayahitaji yawepo. Stoo
 * ya chakula ni MUAMALA - majaribio ya FeedRegressionTest yanahitaji
 * IISIWEPO: yanahesabu salio kutoka sifuri, na yanajaribu hali ya
 * "katalogi haina kinachomfaa mzunguko huu", ambayo haiwezi kuwepo kama
 * kila run inaanza na aina tatu zilizopandwa.
 *
 * Hivyo data hii inaingia kupitia DevDataSeeder (CommandLineRunner ya app)
 * PEKEE. IntegrationTest haiiti bean hii, na hiyo ni chaguo la wazi - si
 * kusahau. Ikihitajika kwenye majaribio siku moja, ni mstari mmoja
 * kwenye resetAndReseedSchema().
 * =====================================================================
 *
 * DATA YA ONYESHO, SI KATALOGI YA KWELI. Aina hizi tatu ni za kuonyesha
 * mtiririko wa slice-2 (ukurasa wa kusimamia katalogi) ukiwa na kitu cha
 * kuonyesha. Hazipandwi na migration wala na seed/*.csv kwa makusudi:
 * seed/permissions.csv ni ya kila mazingira, wakati hizi ni za laptop ya
 * maendeleo pekee, na zinaweza kufutwa au kubadilishwa kupitia UI hiyo
 * bila kuathiri chochote.
 */
@Service
@Profile("dev")
public class DevFeedSeedService {

    private static final Logger logger = LoggerFactory.getLogger(DevFeedSeedService.class);

    /** Shamba la onyesho - lile lile lenye kitengo na mzunguko wa DevSeedService. */
    private static final String DEMO_FARM = "Dev Farm A";

    /**
     * Madirisha yanagusana kwenye mipaka kwa MAKUSUDI ([0,2] na [2,5]):
     * mwezi wa 2 ni wa mpito, ambapo aina mbili ni EXACT kwa wakati mmoja
     * na mkulima anachagua. FeedService.classify inashughulikia hali hiyo
     * bila utata - zote mbili ni EXACT, hakuna inayochujwa.
     */
    private static final List<DemoFeedType> CATALOG = List.of(
            new DemoFeedType("Fry Starter", 0, 2),
            new DemoFeedType("Grower", 2, 5),
            new DemoFeedType("Finisher", 5, 12));

    /**
     * Stoo ya kuanzia (kg) kwa shamba la onyesho, ili salio la kila aina
     * lianze likiwa CHANYA - ukurasa wa stoo usionekane tupu siku ya
     * kwanza. "Finisher" imeachwa bila stoo kwa makusudi: inaonyesha
     * kwamba aina iliyopo kwenye katalogi bila kununuliwa haina mstari wa
     * salio hata kidogo.
     */
    private static final Map<String, BigDecimal> OPENING_STOCK_KG = Map.of(
            "Fry Starter", new BigDecimal("40"),
            "Grower", new BigDecimal("60"));

    private final FeedTypeRepository feedTypeRepository;
    private final FeedStockMovementRepository movementRepository;
    private final FarmRepository farmRepository;

    public DevFeedSeedService(FeedTypeRepository feedTypeRepository,
                               FeedStockMovementRepository movementRepository,
                               FarmRepository farmRepository) {
        this.feedTypeRepository = feedTypeRepository;
        this.movementRepository = movementRepository;
        this.farmRepository = farmRepository;
    }

    /**
     * Idempotent kama DevSeedService: kila kitu ni create-if-not-exists,
     * hivyo restart HAIRUDUFU aina wala HAIONGEZI stoo mara ya pili.
     */
    @Transactional
    public void seed() {
        Map<String, FeedType> types = new LinkedHashMap<>();
        for (DemoFeedType demo : CATALOG) {
            types.put(demo.name(), feedType(demo));
        }

        Optional<Farm> demoFarm = farmByName(DEMO_FARM);
        if (demoFarm.isEmpty()) {
            // DevSeedService ndiyo inayotengeneza shamba hili, na runner
            // inaiita kwanza. Kama halipo, kuna tatizo la mpangilio -
            // katalogi imepandwa, stoo imerukwa, app inaendelea.
            logger.warn("Shamba la onyesho '{}' halipo - stoo ya kuanzia imerukwa.", DEMO_FARM);
            return;
        }

        int added = 0;
        for (Map.Entry<String, BigDecimal> entry : OPENING_STOCK_KG.entrySet()) {
            if (openingStock(demoFarm.get(), types.get(entry.getKey()), entry.getValue())) {
                added++;
            }
        }

        logger.info("Katalogi ya chakula ya dev tayari: {} ({} aina). Stoo ya kuanzia "
                        + "kwenye '{}': mistari {} mipya.",
                String.join(", ", types.keySet()), types.size(), DEMO_FARM, added);
    }

    private FeedType feedType(DemoFeedType demo) {
        return feedTypeRepository.findByName(demo.name()).orElseGet(() -> {
            FeedType feedType = new FeedType();
            feedType.setName(demo.name());
            feedType.setMinAgeMonths(demo.minAgeMonths());
            feedType.setMaxAgeMonths(demo.maxAgeMonths());
            feedType.setActive(true);
            return feedTypeRepository.save(feedType);
        });
    }

    /**
     * Movement ya IN isiyo na ununuzi nyuma yake - "chakula kilichokuwepo
     * kabla hatujaanza kurekodi". FeedStockMovement.referencePurchaseId ni
     * nullable hasa kwa hali hii, na leja tayari inakubali kwamba stoo
     * halisi inaweza kutangulia rekodi zake (angalia FeedRegressionTest.
     * balanceMayGoNegative, upande wa pili wa sarafu ile ile).
     *
     * Kinga ya rudufu ni "shamba hili lina movement yoyote ya aina hii?"
     * badala ya kuhesabu mistari: ikiwa mtu ameshanunua au kulisha aina
     * hiyo wakati wa onyesho, stoo ya kuanzia HAIONGEZWI tena juu yake.
     */
    private boolean openingStock(Farm farm, FeedType feedType, BigDecimal quantityKg) {
        if (feedType == null || movementRepository.existsByFarm_FarmIdAndFeedType_FeedTypeId(
                farm.getFarmId(), feedType.getFeedTypeId())) {
            return false;
        }
        FeedStockMovement movement = new FeedStockMovement();
        movement.setFarm(farm);
        movement.setFeedType(feedType);
        movement.setDirection(FeedStockMovement.Direction.IN);
        movement.setQuantityKg(quantityKg);
        movementRepository.save(movement);
        return true;
    }

    /** farms.name ni UNIQUE tangu V9 - mtindo ule ule wa DevSeedService.farmByName. */
    private Optional<Farm> farmByName(String name) {
        return farmRepository.findAll().stream()
                .filter(farm -> name.equals(farm.getName()))
                .findFirst();
    }

    private record DemoFeedType(String name, int minAgeMonths, int maxAgeMonths) {}
}
