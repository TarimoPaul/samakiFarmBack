package com.samaki.farm.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.feed.entity.FeedType;
import com.samaki.farm.feed.repository.FeedTypeRepository;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI C - Chakula.
 *
 * Module hii ina vitu viwili ambavyo nyingine hazina.
 *
 * KWANZA, LEJA INAYOJIANDIKA. Mteja haandiki feed_stock_movements kamwe -
 * kununua kunazalisha IN, kulisha kunazalisha OUT, ndani ya transaction ile
 * ile. Hesabu hiyo ndiyo inayojibu "kuna chakula kiasi gani stoo", hivyo
 * ikikosea kimyakimya, shamba linapanga kwa namba isiyo ya kweli. Sasa
 * inahesabiwa KWA KILA AINA: salio moja la shamba zima lilikuwa
 * linaghairisha aina zisizoghairishana.
 *
 * PILI, UAMUZI WENYE ATHARI YA KIUSALAMA. feedTypesForCycle inaamua ni
 * chakula gani kinaonyeshwa kwa anayelisha, na sheria yake SI YA
 * ULINGANIFU: samaki mkubwa anaweza kula chakula cha wadogo, mdogo hawezi
 * kula cha wakubwa. Sheria ikigeuzwa upande, mfumo unapendekeza chakula
 * ambacho vifaranga hawawezi kumeza - na hakuna ujumbe wa kosa
 * unaotokea, ni samaki tu wasiokula.
 */
@DisplayName("C - Chakula")
class FeedRegressionTest extends IntegrationTest {

    private static final String BALANCE_QUERY =
            "query { feedStockBalance { feedType { feedTypeId name } quantityKg } }";

    /**
     * Hakuna mutation ya kuzima aina (haikuombwa), hivyo hali ya "aina
     * iliyozimwa" inawekwa moja kwa moja kupitia repository. Ni kuandika
     * kwa DB halisi, hivyo server inaisoma kwenye transaction yake mwenyewe
     * kama data nyingine yoyote.
     */
    @Autowired private FeedTypeRepository feedTypeRepository;

    private Integer pelletId;

    /**
     * Chakula cha kawaida cha majaribio: [0, 12] miezi. Mzunguko wa fixture
     * (cycleA) una umri wa MWEZI MMOJA - angalia DevSeedService, ambapo
     * stockingDate ni leo kutoa mwezi mmoja - hivyo aina hii ni EXACT kwake.
     *
     * INAUNDWA INAPOHITAJIKA, si kwenye @BeforeEach, kwa sababu moja
     * mahususi: aina hii ina min=0, hivyo ni EXACT au SAFE_LOWER kwa KILA
     * umri. Ikiwepo daima, hali ya NO_SUITABLE_FEED - ambayo inahitaji
     * KILA aina iwe na min > umri - isingewezekana kujaribiwa hata kidogo.
     * JUnit inaunda instance mpya kwa kila test, hivyo pelletId inarudi
     * null yenyewe.
     */
    private int pellet() {
        if (pelletId == null) {
            pelletId = createFeedType(adminToken, "PELLET", 0, 12);
        }
        return pelletId;
    }

    // --------------------------------------------------------- misaada

    private int createFeedType(String token, String name, int minAgeMonths, int maxAgeMonths) {
        JsonNode response = graphql(token, createFeedTypeMutation(name, minAgeMonths, maxAgeMonths));
        return response.path("data").path("createFeedType").path("feedTypeId").asInt();
    }

    private String createFeedTypeMutation(String name, int minAgeMonths, int maxAgeMonths) {
        return "mutation { createFeedType(name: \"" + name + "\", minAgeMonths: " + minAgeMonths
                + ", maxAgeMonths: " + maxAgeMonths + ") { feedTypeId name minAgeMonths maxAgeMonths active } }";
    }

    private String purchase(double kg, double unitCost) {
        return purchase(pellet(), kg, unitCost);
    }

    private String purchase(int feedTypeId, double kg, double unitCost) {
        return "mutation { recordFeedPurchase(input: {purchaseDate: \"2026-09-01\", "
                + "feedTypeId: " + feedTypeId + ", quantityKg: " + kg + ", unitCost: " + unitCost
                + ", supplier: \"Duka\"}) { purchaseId quantityKg unitCost totalCost } }";
    }

    private String feeding(double kg) {
        return feeding(pellet(), kg);
    }

    private String feeding(int feedTypeId, double kg) {
        return "mutation { logFeeding(input: {cycleId: " + cycleA + ", quantityKg: " + kg
                + ", feedTypeId: " + feedTypeId + "}) { logId quantityKg recordedByName } }";
    }

    /** Jumla ya safu ZOTE - "kuna kilo ngapi stoo" bila kujali aina. */
    private double balance(String token) {
        double total = 0;
        for (JsonNode row : graphql(token, BALANCE_QUERY).path("data").path("feedStockBalance")) {
            total += row.path("quantityKg").asDouble();
        }
        return total;
    }

    /** Salio la AINA MOJA. Aina isiyo na movement yoyote haina mstari - ni 0. */
    private double balanceOf(String token, int feedTypeId) {
        for (JsonNode row : graphql(token, BALANCE_QUERY).path("data").path("feedStockBalance")) {
            if (row.path("feedType").path("feedTypeId").asInt() == feedTypeId) {
                return row.path("quantityKg").asDouble();
            }
        }
        return 0;
    }

    private void deactivate(int feedTypeId) {
        inTx(() -> {
            FeedType feedType = feedTypeRepository.findById(feedTypeId).orElseThrow();
            feedType.setActive(false);
            return feedTypeRepository.save(feedType);
        });
    }

    private JsonNode suitableFor(String token, int cycleId) {
        return graphql(token, "query { feedTypesForCycle(cycleId: " + cycleId
                + ") { cycleAgeMonths noSuitableFeed feedTypes { suitability feedType { feedTypeId name } } } }")
                .path("data").path("feedTypesForCycle");
    }

    @Nested
    @DisplayName("hesabu ya leja")
    class LedgerMath {

        @Test
        @DisplayName("stoo huanza sifuri")
        void startsEmpty() {
            assertThat(balance(adminToken)).isZero();
            // Hakuna aina yoyote iliyowahi kuhamishwa, hivyo hakuna mstari
            // KABISA - si mstari wa sifuri. COALESCE ya zamani ilirudisha
            // 0.0 kwa sababu ilikuwa namba moja; orodha haina aina ya
            // kuweka kwenye mstari usio na movement.
            assertThat(graphql(adminToken, BALANCE_QUERY).path("data").path("feedStockBalance")).isEmpty();
        }

        @Test
        @DisplayName("kununua kunaongeza stoo na kuzalisha movement ya IN")
        void purchaseAddsStock() {
            assertThat(graphqlErrorCode(graphql(adminToken, purchase(50, 1200)))).isNull();

            assertThat(balance(adminToken)).isEqualTo(50.0);

            JsonNode movements = graphql(adminToken,
                    "query { feedStockMovements { direction quantityKg referencePurchaseId "
                            + "feedType { feedTypeId } } }")
                    .path("data").path("feedStockMovements");
            assertThat(movements).hasSize(1);
            assertThat(movements.get(0).path("direction").asText()).isEqualTo("IN");
            assertThat(movements.get(0).path("quantityKg").asDouble()).isEqualTo(50.0);
            // Leja inaunganishwa na ununuzi uliyoizalisha.
            assertThat(movements.get(0).path("referencePurchaseId").isNull()).isFalse();
            // ...na inabeba aina ya chakula, ndiyo inayofanya salio la kila
            // aina liweze kuhesabiwa.
            assertThat(movements.get(0).path("feedType").path("feedTypeId").asInt()).isEqualTo(pellet());
        }

        @Test
        @DisplayName("kulisha kunapunguza stoo na kuzalisha movement ya OUT")
        void feedingRemovesStock() {
            graphql(adminToken, purchase(50, 1200));

            assertThat(graphqlErrorCode(graphql(workerToken, feeding(12.5)))).isNull();
            assertThat(balance(adminToken)).isEqualTo(37.5);

            JsonNode movements = graphql(adminToken,
                    "query { feedStockMovements { direction quantityKg referenceFeedingLogId } }")
                    .path("data").path("feedStockMovements");
            assertThat(movements).hasSize(2);
            assertThat(movements).anySatisfy(node -> {
                assertThat(node.path("direction").asText()).isEqualTo("OUT");
                assertThat(node.path("quantityKg").asDouble()).isEqualTo(12.5);
                assertThat(node.path("referenceFeedingLogId").isNull()).isFalse();
            });
        }

        @Test
        @DisplayName("manunuzi na ulishaji kadhaa vinajumlishwa kwa usahihi")
        void balanceIsTheRunningSum() {
            graphql(adminToken, purchase(100, 1000));
            graphql(adminToken, purchase(25.5, 1100));
            graphql(workerToken, feeding(30));
            graphql(workerToken, feeding(0.5));

            // 100 + 25.5 - 30 - 0.5
            assertThat(balanceOf(adminToken, pellet())).isEqualTo(95.0);
            assertThat(graphql(adminToken, "query { feedStockMovements { direction } }")
                    .path("data").path("feedStockMovements")).hasSize(4);
        }

        @Test
        @DisplayName("stoo inaweza kwenda hasi - leja inaripoti, haihukumu")
        void balanceMayGoNegative() {
            // Hakuna ununuzi wowote, lakini kulisha kumetokea: hii ni hali
            // halisi ya shambani (chakula kilichokuwepo hakikurekodiwa),
            // na leja inaionyesha badala ya kuikataa.
            graphql(workerToken, feeding(10));

            assertThat(balanceOf(adminToken, pellet())).isEqualTo(-10.0);
        }

        @Test
        @DisplayName("totalCost inakokotolewa na database, si na mteja")
        void totalCostIsComputed() {
            JsonNode created = graphql(adminToken, purchase(50, 1200))
                    .path("data").path("recordFeedPurchase");

            // GENERATED ALWAYS kwenye schema - haiwezi kutumwa na mteja.
            assertThat(created.path("totalCost").asDouble()).isEqualTo(60000.0);
        }
    }

    @Nested
    @DisplayName("salio kwa kila aina ya chakula")
    class PerTypeBalance {

        @Test
        @DisplayName("aina mbili zina masalio yanayojitegemea")
        void balancesAreIndependentPerType() {
            int fry = createFeedType(adminToken, "FRY_CRUMBLE", 0, 0);

            graphql(adminToken, purchase(pellet(), 100, 1000));
            graphql(adminToken, purchase(fry, 40, 1500));
            graphql(workerToken, feeding(pellet(), 30));

            assertThat(balanceOf(adminToken, pellet())).isEqualTo(70.0);
            // Kulisha PELLET hakugusi salio la FRY_CRUMBLE hata kidogo.
            assertThat(balanceOf(adminToken, fry)).isEqualTo(40.0);

            JsonNode rows = graphql(adminToken, BALANCE_QUERY).path("data").path("feedStockBalance");
            assertThat(rows).hasSize(2);
        }

        @Test
        @DisplayName("aina zenye salio +50 na -50 HAZIGHAIRIANI hadi sifuri")
        void oppositeBalancesDoNotCancel() {
            int fry = createFeedType(adminToken, "FRY_CRUMBLE", 0, 0);

            graphql(adminToken, purchase(pellet(), 50, 1000));
            // FRY haijanunuliwa kamwe, lakini imelishwa: salio lake ni -50.
            graphql(workerToken, feeding(fry, 50));

            // Hii ndiyo hasa iliyokuwa ikipotea. Salio moja la shamba zima
            // lingesoma 0.0 hapa - ghala lenye kilo 50 za PELLET likionekana
            // tupu kabisa, na deni la kilo 50 za FRY likifichwa nalo.
            assertThat(balance(adminToken)).isZero();
            assertThat(balanceOf(adminToken, pellet())).isEqualTo(50.0);
            assertThat(balanceOf(adminToken, fry)).isEqualTo(-50.0);
        }

        @Test
        @DisplayName("aina isiyo na movement yoyote haina mstari")
        void untouchedTypeHasNoRow() {
            createFeedType(adminToken, "GROWER", 6, 12);
            graphql(adminToken, purchase(pellet(), 10, 1000));

            JsonNode rows = graphql(adminToken, BALANCE_QUERY).path("data").path("feedStockBalance");
            // Salio ni la LEJA, si la katalogi: aina iliyopo kwenye katalogi
            // bila kuwahi kununuliwa haina kilo za kuripoti.
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).path("feedType").path("name").asText()).isEqualTo("PELLET");
        }
    }

    @Nested
    @DisplayName("sheria ya mwelekeo wa umri")
    class AgeSuitability {

        @Test
        @DisplayName("umri wa mzunguko unakokotolewa na server")
        void serverComputesCycleAge() {
            // Fixture: stockingDate ni leo kutoa mwezi mmoja.
            assertThat(suitableFor(workerToken, cycleA).path("cycleAgeMonths").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("dirisha linalojumuisha umri ni EXACT, na linakuja kwanza")
        void exactComesFirst() {
            // PELLET [0,12] ni EXACT kwa umri 1. FRY [0,0] ni SAFE_LOWER.
            // FRY inaundwa KWANZA kwa makusudi: ikiwa mpangilio ungefuata
            // kuundwa au jina, FRY_CRUMBLE ingekuja kabla ya PELLET. Daraja
            // ndilo linaloamua.
            int fry = createFeedType(adminToken, "FRY_CRUMBLE", 0, 0);
            pellet();

            JsonNode result = suitableFor(workerToken, cycleA);
            JsonNode types = result.path("feedTypes");

            assertThat(result.path("noSuitableFeed").asBoolean()).isFalse();
            assertThat(types).hasSize(2);
            assertThat(types.get(0).path("suitability").asText()).isEqualTo("EXACT");
            assertThat(types.get(0).path("feedType").path("feedTypeId").asInt()).isEqualTo(pellet());
            assertThat(types.get(1).path("suitability").asText()).isEqualTo("SAFE_LOWER");
            assertThat(types.get(1).path("feedType").path("feedTypeId").asInt()).isEqualTo(fry);
        }

        @Test
        @DisplayName("chakula cha wadogo kinarudishwa kama SAFE_LOWER")
        void lowerAgeFeedIsSafe() {
            // Umri 1; dirisha [0,0] limekwisha pitwa - punje ndogo kuliko
            // inavyohitajika, ambayo samaki hawa wanaweza kula.
            int fry = createFeedType(adminToken, "FRY_CRUMBLE", 0, 0);

            JsonNode types = suitableFor(workerToken, cycleA).path("feedTypes");

            assertThat(types).anySatisfy(node -> {
                assertThat(node.path("feedType").path("feedTypeId").asInt()).isEqualTo(fry);
                assertThat(node.path("suitability").asText()).isEqualTo("SAFE_LOWER");
            });
        }

        @Test
        @DisplayName("chakula cha wakubwa HAKIRUDISHWI kabisa - wala kama onyo")
        void higherAgeFeedIsExcludedEntirely() {
            int grower = createFeedType(adminToken, "GROWER", 6, 12);
            pellet();

            JsonNode types = suitableFor(workerToken, cycleA).path("feedTypes");

            // Orodha SI tupu (PELLET ipo), hivyo kutokuwepo kwa GROWER ni
            // uthibitisho wa kweli, si ukweli wa bure wa orodha tupu.
            assertThat(types).hasSize(1);
            // Si kwamba imetajwa kama UNSAFE_HIGHER - HAIPO. Kilicho kwenye
            // orodha ya kuchagua kitachaguliwa na mtu fulani siku fulani.
            assertThat(types).allSatisfy(node ->
                    assertThat(node.path("feedType").path("feedTypeId").asInt()).isNotEqualTo(grower));
            assertThat(types).noneSatisfy(node ->
                    assertThat(node.path("suitability").asText()).isEqualTo("UNSAFE_HIGHER"));
        }

        @Test
        @DisplayName("samaki wadogo + chakula cha wakubwa PEKEE = orodha tupu + NO_SUITABLE_FEED")
        void youngCycleWithOnlyHigherFeedHasNothingSuitable() {
            // MUHIMU: pellet() HAIITWI hapa. Katalogi nzima ni ya samaki
            // wakubwa kuliko wa cycleA (umri 1), ambayo ndiyo hali pekee
            // inayozalisha NO_SUITABLE_FEED.
            createFeedType(adminToken, "GROWER", 6, 12);
            createFeedType(adminToken, "FINISHER", 13, 24);

            JsonNode result = suitableFor(workerToken, cycleA);

            assertThat(result.path("cycleAgeMonths").asInt()).isEqualTo(1);
            // Vyote viwili ni UNSAFE_HIGHER, hivyo vyote vimechujwa - mfumo
            // hauna cha kupendekeza, na unasema hivyo kwa bendera badala ya
            // kuacha orodha tupu ifasiriwe na mteja.
            assertThat(result.path("feedTypes")).isEmpty();
            assertThat(result.path("noSuitableFeed").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("katalogi tupu kabisa ni NO_SUITABLE_FEED pia")
        void emptyCatalogHasNothingSuitable() {
            JsonNode result = suitableFor(workerToken, cycleA);

            assertThat(result.path("feedTypes")).isEmpty();
            // Kwa mtu anayelisha ni tatizo lile lile: hakuna cha kuwapa.
            assertThat(result.path("noSuitableFeed").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("kukiwa na chakula kinachofaa, bendera ni false")
        void flagIsFalseWhenSomethingFits() {
            pellet();
            createFeedType(adminToken, "GROWER", 6, 12);

            JsonNode result = suitableFor(workerToken, cycleA);

            assertThat(result.path("noSuitableFeed").asBoolean()).isFalse();
            // GROWER imechujwa; PELLET pekee imebaki.
            assertThat(result.path("feedTypes")).hasSize(1);
        }

        @Test
        @DisplayName("mzunguko wa shamba jingine ni FORBIDDEN")
        void suitabilityIsFarmScoped() {
            JsonNode response = graphql(workerBToken,
                    "query { feedTypesForCycle(cycleId: " + cycleA + ") { noSuitableFeed } }");

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).isEqualTo("Huruhusiwi kufikia shamba hili.");
        }
    }

    @Nested
    @DisplayName("lango la umri wakati wa kulisha")
    class FeedingAgeGate {

        @Test
        @DisplayName("EXACT inakubaliwa")
        void exactIsAccepted() {
            // PELLET [0,12] dhidi ya umri 1.
            assertThat(graphqlErrorCode(graphql(workerToken, feeding(pellet(), 5)))).isNull();
            assertThat(balanceOf(adminToken, pellet())).isEqualTo(-5.0);
        }

        @Test
        @DisplayName("SAFE_LOWER INAKUBALIWA - si kosa, ni uamuzi")
        void safeLowerIsAccepted() {
            // FRY [0,0] dhidi ya umri 1: chakula cha wadogo kuliko hawa.
            // Samaki hawa wanaweza kukila, na mkulima anaweza kuwa
            // anamalizia mfuko wa mwisho. Server HAIZUII - onyo ni la UI.
            int fry = createFeedType(adminToken, "FRY_CRUMBLE", 0, 0);

            assertThat(graphqlErrorCode(graphql(workerToken, feeding(fry, 4)))).isNull();
            assertThat(balanceOf(adminToken, fry)).isEqualTo(-4.0);
        }

        @Test
        @DisplayName("UNSAFE_HIGHER inakataliwa kwa VALIDATION_ERROR")
        void unsafeHigherIsRejected() {
            // GROWER [6,12] dhidi ya umri 1: punje kubwa kuliko midomo yao.
            int grower = createFeedType(adminToken, "GROWER", 6, 12);

            JsonNode response = graphql(workerToken, feeding(grower, 5));

            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            // Ujumbe unataja namba zote mbili zilizosababisha uamuzi.
            assertThat(graphqlMessage(response)).contains("GROWER").contains("miezi");
        }

        @Test
        @DisplayName("aina iliyozimwa inakataliwa hata ikiwa EXACT")
        void inactiveTypeIsRejected() {
            int retired = createFeedType(adminToken, "RETIRED", 0, 12);
            // Dirisha lake linamfaa kabisa mzunguko huu - kinachoikataa ni
            // kuzimwa pekee, si umri.
            deactivate(retired);

            JsonNode response = graphql(workerToken, feeding(retired, 5));

            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(response)).contains("RETIRED");
        }

        @Test
        @DisplayName("ulishaji uliokataliwa hauachi alama kwenye leja")
        void rejectedFeedingLeavesNoLedgerEntry() {
            int grower = createFeedType(adminToken, "GROWER", 6, 12);
            graphql(adminToken, purchase(grower, 40, 1000));

            graphql(workerToken, feeding(grower, 5));

            // Ununuzi umebaki (kununua chakula cha baadaye ni halali);
            // ulishaji haujaandikwa hata kidogo.
            assertThat(balanceOf(adminToken, grower)).isEqualTo(40.0);
            assertThat(graphql(adminToken, "query { feedingLogs { logId } }")
                    .path("data").path("feedingLogs")).isEmpty();
        }

        @Test
        @DisplayName("kununua chakula cha wakubwa KUNARUHUSIWA - ni stoo ya baadaye")
        void purchaseIsNotAgeGated() {
            // Lango ni la KULISHA, si la kununua: shamba linanunua chakula
            // cha hatua inayofuata kabla samaki hawajafika hapo, na hakuna
            // mzunguko wa kulinganisha nao wakati wa ununuzi.
            int grower = createFeedType(adminToken, "GROWER", 6, 12);

            assertThat(graphqlErrorCode(graphql(adminToken, purchase(grower, 40, 1000)))).isNull();
        }
    }

    @Nested
    @DisplayName("katalogi ya aina za chakula")
    class Catalog {

        @Test
        @DisplayName("createFeedType inahifadhi dirisha la umri na inaanza ikitumika")
        void createStoresTheAgeWindow() {
            JsonNode created = graphql(adminToken, createFeedTypeMutation("STARTER", 1, 3))
                    .path("data").path("createFeedType");

            assertThat(created.path("name").asText()).isEqualTo("STARTER");
            assertThat(created.path("minAgeMonths").asInt()).isEqualTo(1);
            assertThat(created.path("maxAgeMonths").asInt()).isEqualTo(3);
            assertThat(created.path("active").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("dirisha lililopinduka (max < min) linakataliwa")
        void invertedWindowIsRejected() {
            // Aina kama hii ingekuwa UNSAFE_HIGHER kwa KILA umri - haifai
            // samaki yeyote, na ingekaa kimya kwenye katalogi.
            JsonNode response = graphql(adminToken, createFeedTypeMutation("BAD", 12, 6));

            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("min == max ni halali - chakula cha mwezi mmoja")
        void singleMonthWindowIsValid() {
            assertThat(graphqlErrorCode(graphql(adminToken, createFeedTypeMutation("FRY", 0, 0)))).isNull();
        }

        @Test
        @DisplayName("umri hasi unakataliwa")
        void negativeAgeIsRejected() {
            assertThat(graphqlErrorCode(graphql(adminToken, createFeedTypeMutation("BAD", -1, 5))))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("jina linalojirudia linakataliwa na database")
        void duplicateNameIsRejected() {
            pellet();

            assertThat(graphqlErrorCode(graphql(adminToken, createFeedTypeMutation("PELLET", 0, 12))))
                    .isEqualTo("CONFLICT");
        }

        @Test
        @DisplayName("feedTypes inaorodhesha katalogi")
        void listReturnsTheCatalog() {
            pellet();
            createFeedType(adminToken, "GROWER", 6, 12);

            JsonNode types = graphql(adminToken, "query { feedTypes { feedTypeId name } }")
                    .path("data").path("feedTypes");

            assertThat(types).hasSize(2);
        }

        @Test
        @DisplayName("aina isiyojulikana kwenye ununuzi/ulishaji inakataliwa")
        void unknownFeedTypeIsRejected() {
            assertThat(graphqlErrorCode(graphql(adminToken, purchase(999999, 10, 1000))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(graphql(workerToken, feeding(999999, 10))))
                    .isEqualTo("VALIDATION_ERROR");
        }
    }

    @Nested
    @DisplayName("lango la ruhusa")
    class Permissions {

        @Test
        @DisplayName("WORKER hawezi kununua - hana manage_feed_stock")
        void workerCannotPurchase() {
            JsonNode response = graphql(workerToken, purchase(50, 1200));

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("manage_feed_stock");
        }

        @Test
        @DisplayName("WORKER anaweza kulisha - ana log_feeding")
        void workerCanFeed() {
            assertThat(graphqlErrorCode(graphql(workerToken, feeding(5)))).isNull();
        }

        @Test
        @DisplayName("VIEWER hawezi kulisha wala kununua")
        void viewerCanDoNeither() {
            assertThat(graphqlErrorCode(graphql(viewerToken, feeding(5)))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(viewerToken, purchase(10, 100)))).isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("WORKER hawezi kuunda aina ya chakula - hana manage_feed_stock")
        void workerCannotCreateFeedType() {
            JsonNode response = graphql(workerToken, createFeedTypeMutation("STARTER", 1, 3));

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("manage_feed_stock");
        }

        @Test
        @DisplayName("VIEWER hawezi kuunda wala kuorodhesha katalogi")
        void viewerCannotTouchTheCatalog() {
            assertThat(graphqlErrorCode(graphql(viewerToken, createFeedTypeMutation("STARTER", 1, 3))))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(viewerToken, "query { feedTypes { feedTypeId } }")))
                    .isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("WORKER hawezi kuorodhesha katalogi - ni ukurasa wa usimamizi")
        void workerCannotListTheCatalog() {
            // WORKER anachohitaji ni feedTypesForCycle (kinachomfaa samaki
            // hawa), si katalogi nzima ya kuisimamia.
            JsonNode response = graphql(workerToken, "query { feedTypes { feedTypeId } }");

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("manage_feed_stock");
        }
    }

    @Nested
    @DisplayName("lango la view_feed_stock")
    class FeedStockGate {

        @Test
        @DisplayName("asiye na role hawezi kusoma salio")
        void noRoleCannotReadBalance() {
            JsonNode response = graphql(noroleToken, BALANCE_QUERY);

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("view_feed_stock");
        }

        @Test
        @DisplayName("VIEWER ana view_dashboard lakini SI view_feed_stock")
        void viewerIsShutOutOfStock() {
            // Hii ndiyo maana yote ya kugawa ruhusa: VIEWER anaendelea kuona
            // ripoti, lakini si salio la ghala wala maelekezo ya kulisha.
            assertThat(graphqlErrorCode(graphql(viewerToken, BALANCE_QUERY))).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(graphql(viewerToken, BALANCE_QUERY))).contains("view_feed_stock");

            JsonNode suitability = graphql(viewerToken,
                    "query { feedTypesForCycle(cycleId: " + cycleA + ") { noSuitableFeed } }");
            assertThat(graphqlErrorCode(suitability)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(suitability)).contains("view_feed_stock");
        }

        @Test
        @DisplayName("VIEWER bado anaona feedPurchases - hiyo imebaki view_dashboard")
        void viewerStillSeesPurchases() {
            // Ruhusa imegawanywa, si kufungwa kote: matumizi ya fedha ni kazi
            // ya ripoti, na hayakuhama.
            assertThat(graphqlErrorCode(graphql(viewerToken,
                    "query { feedPurchases { purchaseId } }"))).isNull();
        }

        @Test
        @DisplayName("WORKER ana view_feed_stock - ndiye analisha")
        void workerCanReadStock() {
            assertThat(graphqlErrorCode(graphql(workerToken, BALANCE_QUERY))).isNull();
            assertThat(graphqlErrorCode(graphql(workerToken,
                    "query { feedTypesForCycle(cycleId: " + cycleA + ") { noSuitableFeed } }"))).isNull();
        }
    }

    @Nested
    @DisplayName("D-1: kufungwa kwa shamba")
    class FarmScoping {

        @Test
        @DisplayName("salio la shamba jingine halionekani")
        void balanceIsPerFarm() {
            graphql(adminToken, purchase(80, 1000));

            // Shamba B halijanunua chochote, na halioni chochote cha A.
            assertThat(balance(workerBToken)).isZero();
        }

        @Test
        @DisplayName("kulisha mzunguko wa shamba jingine ni FORBIDDEN")
        void feedingAnotherFarmsCycleIsForbidden() {
            JsonNode response = graphql(workerBToken, feeding(5));

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).isEqualTo("Huruhusiwi kufikia shamba hili.");
        }

        @Test
        @DisplayName("leja ya shamba jingine haionekani")
        void movementsAreScoped() {
            graphql(adminToken, purchase(80, 1000));
            graphql(workerToken, feeding(10));

            assertThat(graphql(workerBToken, "query { feedStockMovements { direction } }")
                    .path("data").path("feedStockMovements")).isEmpty();
            assertThat(graphql(workerBToken, "query { feedingLogs { logId } }")
                    .path("data").path("feedingLogs")).isEmpty();
        }

        @Test
        @DisplayName("katalogi ya aina ni YA KIMFUMO - inaonekana kwa mashamba yote")
        void catalogIsGlobal() {
            // Tofauti na leja: feed_types haina farm_id kwa makusudi, hivyo
            // aina iliyoundwa na shamba A inapatikana kwa B pia - hakuna
            // farm_id kwenye feed_types.
            pellet();
            createFeedType(adminToken, "GROWER", 6, 12);

            // WORKER wa shamba B anaona aina zote mbili kupitia mzunguko
            // wake? Hana mzunguko; lakini katalogi yenyewe si ya shamba,
            // hivyo hesabu ya OWNER ndiyo hesabu kamili.
            JsonNode types = graphql(adminToken, "query { feedTypes { name } }")
                    .path("data").path("feedTypes");
            assertThat(types).hasSize(2);
        }
    }

    @Nested
    @DisplayName("kukataliwa kimuundo")
    class Validation {

        @Test
        @DisplayName("kiasi sifuri au hasi kinakataliwa kwa VALIDATION_ERROR")
        void nonPositiveQuantityIsRejected() {
            assertThat(graphqlErrorCode(graphql(workerToken, feeding(0))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(graphql(workerToken, feeding(-5))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(graphql(adminToken, purchase(-1, 1000))))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("mzunguko usiojulikana")
        void unknownCycle() {
            JsonNode response = graphql(workerToken,
                    "mutation { logFeeding(input: {cycleId: 999999, quantityKg: 5, feedTypeId: "
                            + pellet() + "}) { logId } }");

            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("ombi lililokataliwa haliachi alama kwenye leja")
        void rejectedRequestsLeaveNoLedgerEntry() {
            graphql(workerToken, feeding(0));
            graphql(adminToken, purchase(-1, 1000));

            assertThat(balance(adminToken)).isZero();
            assertThat(graphql(adminToken, "query { feedStockMovements { direction } }")
                    .path("data").path("feedStockMovements")).isEmpty();
        }
    }
}
