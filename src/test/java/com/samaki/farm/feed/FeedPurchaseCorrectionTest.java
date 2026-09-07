package com.samaki.farm.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI C3 - kurekebisha manunuzi ya chakula.
 *
 * UNUNUZI SI SAFU PEKE YAKE, na hilo ndilo linaloeleza kila uamuzi hapa.
 * recordFeedPurchase inaandika vitu viwili: safu ya manunuzi NA movement ya
 * IN kwenye leja. Jumla ya movement ndiyo `feedStockBalance` - namba ile ile
 * inayoonekana kwenye panel ya stoo ya skrini ya Malisho na kwenye onyo la
 * "chakula kinakwisha".
 *
 * Kwa hiyo jaribio la maana hapa si "je, safu imetoweka?" bali "je, KILO
 * zimerudi?". Karibu kila test hapa chini inapima salio, si orodha.
 *
 * KUBATILISHA, SI KUFUTA. Rekodi ya kurekebisha (movement ya OUT ya kilo
 * zile zile) inarudisha salio bila kufuta historia: ununuzi unabaki
 * ukionekana ukiwa `reversed: true`. Leja ni ya kuongezwa tu, na doc ya
 * FeedStockMovement inasema hivyo.
 */
@DisplayName("C3 - Kurekebisha manunuzi")
class FeedPurchaseCorrectionTest extends IntegrationTest {

    private static final String BALANCE_QUERY =
            "query { feedStockBalance { feedType { feedTypeId } quantityKg } }";
    private static final String PURCHASES_QUERY =
            "query { feedPurchases { purchaseId quantityKg unitCost totalCost supplier reversed "
                    + "feedType { feedTypeId name } } }";

    // --------------------------------------------------------- misaada

    private int feedType(String name) {
        JsonNode res = graphql(adminToken, "mutation { createFeedType(name: \"" + name
                + "\", minAgeMonths: 0, maxAgeMonths: 12) { feedTypeId } }");
        return res.path("data").path("createFeedType").path("feedTypeId").asInt();
    }

    private int purchase(int feedTypeId, double kg, double unitCost) {
        JsonNode res = graphql(adminToken, recordMutation(feedTypeId, kg, unitCost));
        return res.path("data").path("recordFeedPurchase").path("purchaseId").asInt();
    }

    private String recordMutation(int feedTypeId, double kg, double unitCost) {
        return "mutation { recordFeedPurchase(input: {purchaseDate: \"2026-09-01\", feedTypeId: "
                + feedTypeId + ", quantityKg: " + kg + ", unitCost: " + unitCost
                + ", supplier: \"Duka\"}) { purchaseId quantityKg totalCost reversed } }";
    }

    private String reverseMutation(int purchaseId) {
        return "mutation { reverseFeedPurchase(purchaseId: " + purchaseId
                + ") { purchaseId quantityKg reversed } }";
    }

    private String correctMutation(int purchaseId, int feedTypeId, double kg, double unitCost) {
        return "mutation { correctFeedPurchase(purchaseId: " + purchaseId
                + ", input: {purchaseDate: \"2026-09-01\", feedTypeId: " + feedTypeId
                + ", quantityKg: " + kg + ", unitCost: " + unitCost
                + ", supplier: \"Duka\"}) { purchaseId quantityKg totalCost reversed } }";
    }

    /** Salio la aina moja. Aina isiyo na movement yoyote haina mstari - ni 0. */
    private double balanceOf(int feedTypeId) {
        for (JsonNode row : graphql(adminToken, BALANCE_QUERY).path("data").path("feedStockBalance")) {
            if (row.path("feedType").path("feedTypeId").asInt() == feedTypeId) {
                return row.path("quantityKg").asDouble();
            }
        }
        return 0;
    }

    private JsonNode purchaseRow(int purchaseId) {
        for (JsonNode row : graphql(adminToken, PURCHASES_QUERY).path("data").path("feedPurchases")) {
            if (row.path("purchaseId").asInt() == purchaseId) {
                return row;
            }
        }
        return null;
    }

    // =====================================================================

    @Nested
    @DisplayName("kubatilisha")
    class Reversing {

        @Test
        @DisplayName("KILO ZINARUDI - ndilo jambo zima")
        void returnsTheKilos() {
            int type = feedType("PELLET_R1");
            int id = purchase(type, 50, 1200);
            assertThat(balanceOf(type)).isEqualTo(50);

            assertThat(graphqlErrorCode(graphql(adminToken, reverseMutation(id)))).isNull();

            // Si "karibu sifuri" - movement ya OUT ni kilo ZILE ZILE.
            assertThat(balanceOf(type)).isEqualTo(0);
        }

        @Test
        @DisplayName("ununuzi UNABAKI kwenye orodha, ukiwa umebatilishwa")
        void keepsTheRowVisible() {
            int type = feedType("PELLET_R2");
            int id = purchase(type, 50, 1200);

            graphql(adminToken, reverseMutation(id));

            JsonNode row = purchaseRow(id);
            // Haijafutwa - hii ndiyo tofauti kati ya kubatilisha na kufuta.
            assertThat(row).isNotNull();
            assertThat(row.path("reversed").asBoolean()).isTrue();
            // Na bado inabeba kilo zake za awali: historia inasema ununuzi
            // ULITOKEA, kisha ukabatilishwa - si kwamba haukuwahi kutokea.
            assertThat(row.path("quantityKg").asDouble()).isEqualTo(50);
        }

        @Test
        @DisplayName("mara ya pili INAKATALIWA - vinginevyo kilo zingeondoka mara mbili")
        void refusesASecondReversal() {
            int type = feedType("PELLET_R3");
            int id = purchase(type, 50, 1200);
            graphql(adminToken, reverseMutation(id));

            JsonNode second = graphql(adminToken, reverseMutation(id));

            assertThat(graphqlErrorCode(second)).isEqualTo("PURCHASE_ALREADY_REVERSED");
            // NAMBA NDIYO SABABU: bila kikwazo, salio lingekuwa -50.
            assertThat(balanceOf(type)).isEqualTo(0);
        }

        @Test
        @DisplayName("ununuzi mwingine wa aina ile ile haujaguswa")
        void leavesOtherPurchasesAlone() {
            int type = feedType("PELLET_R4");
            int first = purchase(type, 50, 1200);
            purchase(type, 30, 1100);
            assertThat(balanceOf(type)).isEqualTo(80);

            graphql(adminToken, reverseMutation(first));

            // Kilo za ununuzi wa pili pekee ndizo zilizobaki.
            assertThat(balanceOf(type)).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("kurekebisha")
    class Correcting {

        @Test
        @DisplayName("salio linaishia kwenye kiasi KIPYA, si jumla ya viwili")
        void endsAtTheNewQuantity() {
            int type = feedType("PELLET_C1");
            int id = purchase(type, 50, 1200);

            JsonNode res = graphql(adminToken, correctMutation(id, type, 35, 1300));

            assertThat(graphqlErrorCode(res)).isNull();
            // 50 iliyoingia, 50 iliyobatilishwa, 35 mpya = 35. Si 85.
            assertThat(balanceOf(type)).isEqualTo(35);
        }

        @Test
        @DisplayName("inarudisha ununuzi MPYA, na wa zamani unabaki umebatilishwa")
        void returnsTheNewPurchase() {
            int type = feedType("PELLET_C2");
            int id = purchase(type, 50, 1200);

            JsonNode res = graphql(adminToken, correctMutation(id, type, 35, 1300));
            int newId = res.path("data").path("correctFeedPurchase").path("purchaseId").asInt();

            assertThat(newId).isNotEqualTo(id);
            assertThat(res.path("data").path("correctFeedPurchase").path("reversed").asBoolean())
                    .isFalse();
            // totalCost inakokotolewa na database kwa namba MPYA.
            assertThat(res.path("data").path("correctFeedPurchase").path("totalCost").asDouble())
                    .isEqualTo(35 * 1300.0);

            assertThat(purchaseRow(id).path("reversed").asBoolean()).isTrue();
            assertThat(purchaseRow(newId).path("reversed").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("aina ya chakula inaweza kubadilika, na kilo zinahama nayo")
        void movesKilosBetweenTypes() {
            int wrong = feedType("PELLET_C3_WRONG");
            int right = feedType("PELLET_C3_RIGHT");
            int id = purchase(wrong, 50, 1200);

            graphql(adminToken, correctMutation(id, right, 50, 1200));

            // Ununuzi uliorekodiwa chini ya aina isiyo sahihi ndilo kosa
            // linaloharibu zaidi: kilo zinaonekana ghalani kwa samaki
            // wasioweza kula chakula hicho.
            assertThat(balanceOf(wrong)).isEqualTo(0);
            assertThat(balanceOf(right)).isEqualTo(50);
        }

        @Test
        @DisplayName("kurekebisha uliobatilishwa INAKATALIWA")
        void refusesToCorrectAReversedPurchase() {
            int type = feedType("PELLET_C4");
            int id = purchase(type, 50, 1200);
            graphql(adminToken, reverseMutation(id));

            JsonNode res = graphql(adminToken, correctMutation(id, type, 35, 1300));

            assertThat(graphqlErrorCode(res)).isEqualTo("PURCHASE_ALREADY_REVERSED");
            assertThat(balanceOf(type)).isEqualTo(0);
        }

        @Test
        @DisplayName("kiasi batili kinakataliwa, na salio la awali HALIGUSWI")
        void invalidInputLeavesTheLedgerAlone() {
            int type = feedType("PELLET_C5");
            int id = purchase(type, 50, 1200);

            JsonNode res = graphql(adminToken, correctMutation(id, type, 0, 1300));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            // KWA NINI HII NI TRANSACTION MOJA: kama ubatilishaji ungekuwa
            // ombi lake, ungekuwa umeshapita hapa - na shamba lingebaki na
            // sifuri badala ya 50, kwa sababu ombi la PILI limeshindwa.
            assertThat(balanceOf(type)).isEqualTo(50);
            assertThat(purchaseRow(id).path("reversed").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("bendera ya `reversed` kwenye orodha")
    class ReversedFlag {

        @Test
        @DisplayName("ni false kwa ununuzi wa kawaida")
        void isFalseByDefault() {
            int type = feedType("PELLET_F1");
            int id = purchase(type, 50, 1200);

            assertThat(purchaseRow(id).path("reversed").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("HAIFICHWI kwa asiye na view_feed_cost - ubatilishaji si bei")
        void isNotMasked() {
            int type = feedType("PELLET_F2");
            int id = purchase(type, 50, 1200);
            graphql(adminToken, reverseMutation(id));

            // WORKER ana view_dashboard bila view_feed_cost.
            JsonNode rows = graphql(workerToken, PURCHASES_QUERY).path("data").path("feedPurchases");
            JsonNode row = null;
            for (JsonNode candidate : rows) {
                if (candidate.path("purchaseId").asInt() == id) {
                    row = candidate;
                }
            }

            assertThat(row).isNotNull();
            // Bei imefichwa...
            assertThat(row.path("unitCost").isNull()).isTrue();
            assertThat(row.path("totalCost").isNull()).isTrue();
            // ...lakini ubatilishaji si bei: anayepanga kulisha anahitaji
            // kujua kwamba magunia haya hayako ghalani.
            assertThat(row.path("reversed").asBoolean()).isTrue();
            assertThat(row.path("quantityKg").asDouble()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("ruhusa na mipaka ya shamba")
    class Permissions {

        @Test
        @DisplayName("mwenye log_feeding pekee hawezi kubatilisha wala kurekebisha")
        void feederCannotCorrect() {
            int type = feedType("PELLET_P1");
            int id = purchase(type, 50, 1200);

            assertThat(graphqlErrorCode(graphql(workerToken, reverseMutation(id))))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(workerToken, correctMutation(id, type, 10, 900))))
                    .isEqualTo("FORBIDDEN");

            // Hakuna kilichopita - kilo zipo pale pale.
            assertThat(balanceOf(type)).isEqualTo(50);
        }

        @Test
        @DisplayName("ununuzi wa shamba jingine hauonekani wala haubatilishiki")
        void cannotReachAnotherFarmsPurchase() {
            int type = feedType("PELLET_P2");
            int id = purchase(type, 50, 1200);

            // workerB yuko shamba B; ununuzi huu ni wa shamba A.
            JsonNode res = graphql(workerBToken, reverseMutation(id));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(balanceOf(type)).isEqualTo(50);
        }
    }
}
