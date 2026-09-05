package com.samaki.farm.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.feed.entity.FeedType;
import com.samaki.farm.feed.repository.FeedStockMovementRepository;
import com.samaki.farm.feed.repository.FeedTypeRepository;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data ya ONYESHO ya chakula (DevFeedSeedService).
 *
 * Seeder inayojirudia ni hatari mahususi: inaendeshwa kwa KILA restart ya
 * app ya dev, hivyo kinga yake ya rudufu ikivunjika, stoo ya onyesho
 * inapanda kwa kilo 100 kila siku bila mtu kugundua - na kila onyesho
 * linaanza na namba tofauti.
 *
 * MUHIMU: harness (IntegrationTest.resetAndReseedSchema) HAIITI seeder
 * hii - inaita devSeedService.seed() pekee. Ndiyo sababu majaribio ya
 * FeedRegressionTest yanaanza na katalogi tupu, na ndiyo sababu HAPA
 * seed() inaitwa kwa mkono: hii ndiyo sehemu pekee inayoijaribu.
 */
@DisplayName("Dev - data ya onyesho ya chakula")
class DevFeedSeedRegressionTest extends IntegrationTest {

    @Autowired private DevFeedSeedService devFeedSeedService;
    @Autowired private FeedTypeRepository feedTypeRepository;
    @Autowired private FeedStockMovementRepository movementRepository;

    private JsonNode balanceRows() {
        return graphql(adminToken,
                "query { feedStockBalance { feedType { name minAgeMonths maxAgeMonths active } quantityKg } }")
                .path("data").path("feedStockBalance");
    }

    private double balanceOf(String feedTypeName) {
        for (JsonNode row : balanceRows()) {
            if (feedTypeName.equals(row.path("feedType").path("name").asText())) {
                return row.path("quantityKg").asDouble();
            }
        }
        return 0;
    }

    @Nested
    @DisplayName("kupanda mara ya kwanza")
    class FirstRun {

        @Test
        @DisplayName("harness haianzi na data hii - ndiyo maana FeedRegressionTest inaanza tupu")
        void harnessStartsWithoutIt() {
            assertThat(feedTypeRepository.findAll()).isEmpty();
            assertThat(movementRepository.findByFarm_FarmIdOrderByMovedAtDesc(farmA)).isEmpty();
        }

        @Test
        @DisplayName("aina tatu zinapandwa na madirisha yao ya umri")
        void seedsThreeFeedTypes() {
            devFeedSeedService.seed();

            JsonNode types = graphql(adminToken,
                    "query { feedTypes { name minAgeMonths maxAgeMonths active } }")
                    .path("data").path("feedTypes");

            assertThat(types).hasSize(3);
            assertThat(types).anySatisfy(node -> {
                assertThat(node.path("name").asText()).isEqualTo("Fry Starter");
                assertThat(node.path("minAgeMonths").asInt()).isZero();
                assertThat(node.path("maxAgeMonths").asInt()).isEqualTo(2);
            });
            assertThat(types).anySatisfy(node -> {
                assertThat(node.path("name").asText()).isEqualTo("Grower");
                assertThat(node.path("minAgeMonths").asInt()).isEqualTo(2);
                assertThat(node.path("maxAgeMonths").asInt()).isEqualTo(5);
            });
            assertThat(types).anySatisfy(node -> {
                assertThat(node.path("name").asText()).isEqualTo("Finisher");
                assertThat(node.path("minAgeMonths").asInt()).isEqualTo(5);
                assertThat(node.path("maxAgeMonths").asInt()).isEqualTo(12);
            });
            assertThat(types).allSatisfy(node ->
                    assertThat(node.path("active").asBoolean()).isTrue());
        }

        @Test
        @DisplayName("salio la kila aina linaanza likiwa CHANYA")
        void openingStockIsPositive() {
            devFeedSeedService.seed();

            assertThat(balanceOf("Fry Starter")).isEqualTo(40.0);
            assertThat(balanceOf("Grower")).isEqualTo(60.0);
            // Finisher ipo kwenye katalogi lakini haijanunuliwa - hivyo
            // haina mstari wa salio kabisa.
            assertThat(balanceRows()).hasSize(2);
        }

        @Test
        @DisplayName("stoo ni ya shamba la onyesho PEKEE")
        void stockIsOnlyOnTheDemoFarm() {
            devFeedSeedService.seed();

            // Shamba B linaona katalogi (ni ya kimfumo) lakini si stoo.
            assertThat(graphql(workerBToken,
                    "query { feedStockBalance { quantityKg } }")
                    .path("data").path("feedStockBalance")).isEmpty();
        }

        @Test
        @DisplayName("katalogi ya onyesho inashirikiana na sheria ya umri")
        void seededCatalogFeedsTheSuitabilityRule() {
            devFeedSeedService.seed();

            // cycleA ana umri wa mwezi 1: Fry Starter [0,2] ni EXACT;
            // Grower [2,5] na Finisher [5,12] ni za wakubwa - zimechujwa.
            JsonNode result = graphql(workerToken,
                    "query { feedTypesForCycle(cycleId: " + cycleA
                            + ") { noSuitableFeed feedTypes { suitability feedType { name } } } }")
                    .path("data").path("feedTypesForCycle");

            assertThat(result.path("noSuitableFeed").asBoolean()).isFalse();
            assertThat(result.path("feedTypes")).hasSize(1);
            assertThat(result.path("feedTypes").get(0).path("feedType").path("name").asText())
                    .isEqualTo("Fry Starter");
            assertThat(result.path("feedTypes").get(0).path("suitability").asText()).isEqualTo("EXACT");
        }
    }

    @Nested
    @DisplayName("kinga ya rudufu (restart)")
    class Idempotency {

        @Test
        @DisplayName("kupanda mara tatu hakuongezi aina wala kilo")
        void repeatedSeedingChangesNothing() {
            devFeedSeedService.seed();
            devFeedSeedService.seed();
            devFeedSeedService.seed();

            assertThat(feedTypeRepository.findAll()).hasSize(3);
            // Hii ndiyo hasa inayoumiza kama kinga ingevunjika: kilo 40
            // zingekuwa 120 baada ya restart mbili.
            assertThat(balanceOf("Fry Starter")).isEqualTo(40.0);
            assertThat(balanceOf("Grower")).isEqualTo(60.0);
            assertThat(movementRepository.findByFarm_FarmIdOrderByMovedAtDesc(farmA)).hasSize(2);
        }

        @Test
        @DisplayName("stoo ya kuanzia hairudishwi juu ya matumizi halisi ya onyesho")
        void seedingDoesNotTopUpAfterRealActivity() {
            devFeedSeedService.seed();

            FeedType fryStarter = feedTypeRepository.findByName("Fry Starter").orElseThrow();
            graphql(workerToken, "mutation { logFeeding(input: {cycleId: " + cycleA
                    + ", quantityKg: 15, feedTypeId: " + fryStarter.getFeedTypeId() + "}) { logId } }");
            assertThat(balanceOf("Fry Starter")).isEqualTo(25.0);

            // Restart: seeder inaona shamba tayari lina historia ya aina
            // hii, hivyo HAIJAZI tena. Kama ingejaza, chakula kilicholiwa
            // kingerudi stoo kimyakimya.
            devFeedSeedService.seed();

            assertThat(balanceOf("Fry Starter")).isEqualTo(25.0);
        }

        @Test
        @DisplayName("aina iliyohaririwa kwa mkono haifutwi wala kurudishwa")
        void manualEditsSurviveReseeding() {
            devFeedSeedService.seed();

            FeedType grower = feedTypeRepository.findByName("Grower").orElseThrow();
            inTx(() -> {
                grower.setActive(false);
                return feedTypeRepository.save(grower);
            });

            devFeedSeedService.seed();

            // create-if-not-exists: jina lipo, hivyo rekodi haiguswi kabisa.
            // Ndicho kinachofanya katalogi ya onyesho iweze kubadilishwa
            // kupitia UI ya slice-2 bila restart kuirudisha nyuma.
            assertThat(feedTypeRepository.findByName("Grower").orElseThrow().isActive()).isFalse();
            assertThat(feedTypeRepository.findAll()).hasSize(3);
        }
    }
}
