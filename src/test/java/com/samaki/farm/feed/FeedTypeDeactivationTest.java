package com.samaki.farm.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI C3 - KUZIMA aina ya chakula kama SWICHI, pamoja na onyo la kabla.
 *
 * Kuzima si kufuta na si kitendo cha mwisho: `active` ni bendera moja
 * inayogeuzwa pande zote mbili. Majaribio haya yanaishikilia sifa hiyo
 * mahali ambapo ni RAHISI KUIVUNJA bila kuiona - siyo kwenye bendera
 * yenyewe (FeedTypeCatalogTest inaigusa hiyo), bali kwenye MZUNGUKO KAMILI:
 *
 *      inalishwa -> inazimwa -> ulishaji unakataliwa -> inarudishwa ->
 *      inalishwa TENA.
 *
 * Hatua ya mwisho ndiyo yenye thamani. Kikwazo chochote kitakachoongezwa
 * siku moja kwenye njia ya kuzima - "usizime yenye stoo", "usizime yenye
 * mizunguko" - kingeifanya hatua hiyo ishindwe, na ndipo swichi ingekuwa
 * imegeuka mlango wa njia moja bila mtu kukusudia.
 *
 * =====================================================================
 * ONYO NI QUERY, KIKWAZO HAKIPO - NA NDIYO MAANA YA NUSU YA MAJARIBIO HAPA
 *
 * feedTypeDeactivationImpact inajibu maswali ambayo kikwazo KINGEKUWA
 * kimeyatumia kukataa: kuna kilo ngapi, na ni samaki gani wataachwa bila
 * chakula kilichokusudiwa. Tofauti ni nani anaamua. Hivyo majaribio
 * yanahakiki mambo mawili kwa pamoja: kwamba namba ni SAHIHI, na kwamba
 * kuzima KUNAPITA hata namba zote mbili zikiwa kubwa.
 *
 * Hesabu ya "mzunguko tegemezi" ndiyo yenye utata halisi, na ndiyo
 * iliyojaribiwa kwa hali nne tofauti: onyo linalolia kwa mzunguko ambao
 * bado una cha kula ni onyo litakalopuuzwa, na siku litakapolia kwa haki
 * hakuna atakayelisikia.
 * =====================================================================
 *
 * FIXTURE: katalogi INAANZA TUPU (IntegrationTest haiiti DevFeedSeedService
 * - angalia doc yake), hivyo kila jaribio linaunda aina linazozihitaji na
 * hakuna nyingine ya kuingilia hesabu. `cycleA` ni mzunguko WA PEKEE
 * unaoendelea wa shamba A, uliopandwa MWEZI MMOJA uliopita - hivyo umri
 * wake ni miezi 1, na madirisha hapa chini yamechaguliwa kwa namba hiyo.
 */
@DisplayName("C3 - Kuzima aina ya chakula (swichi + onyo)")
class FeedTypeDeactivationTest extends IntegrationTest {

    /** Umri wa `cycleA` kwa miezi - angalia DevSeedService.cycle. */
    private static final int CYCLE_AGE_MONTHS = 1;

    /**
     * Kubadilisha hali ya mzunguko kunahitaji repository: hakuna mutation ya
     * kuvuna bado, na jaribio linalohitaji mzunguko usio ACTIVE haliwezi
     * kuusubiri.
     */
    @Autowired private CycleRepository cycleRepository;

    // --------------------------------------------------------- misaada

    private int createFeedType(String name, int min, int max) {
        JsonNode res = graphql(adminToken, "mutation { createFeedType(name: \"" + name
                + "\", minAgeMonths: " + min + ", maxAgeMonths: " + max + ") { feedTypeId } }");
        assertThat(graphqlErrorCode(res)).isNull();
        return res.path("data").path("createFeedType").path("feedTypeId").asInt();
    }

    private String setActiveMutation(int id, boolean active) {
        return "mutation { setFeedTypeActive(feedTypeId: " + id + ", active: " + active
                + ") { feedTypeId active } }";
    }

    private JsonNode setActive(int id, boolean active) {
        return graphql(adminToken, setActiveMutation(id, active));
    }

    private String impactQuery(int id) {
        return "query { feedTypeDeactivationImpact(feedTypeId: " + id
                + ") { remainingKg dependentActiveCycleCount } }";
    }

    private JsonNode impact(int id) {
        JsonNode res = graphql(adminToken, impactQuery(id));
        assertThat(graphqlErrorCode(res)).isNull();
        return res.path("data").path("feedTypeDeactivationImpact");
    }

    private double remainingKg(int id) {
        return impact(id).path("remainingKg").asDouble();
    }

    private int dependentCycles(int id) {
        return impact(id).path("dependentActiveCycleCount").asInt();
    }

    private void purchase(int feedTypeId, int quantityKg) {
        JsonNode res = graphql(adminToken, "mutation { recordFeedPurchase(input: {purchaseDate: "
                + "\"2026-09-01\", feedTypeId: " + feedTypeId + ", quantityKg: " + quantityKg
                + ", unitCost: 1200, supplier: \"Duka\"}) { purchaseId } }");
        assertThat(graphqlErrorCode(res)).isNull();
    }

    private JsonNode feed(int feedTypeId, int quantityKg) {
        return graphql(workerToken, "mutation { logFeeding(input: {cycleId: " + cycleA
                + ", quantityKg: " + quantityKg + ", feedTypeId: " + feedTypeId + "}) { logId } }");
    }

    /** Salio la aina hii kama linavyosomwa na skrini ya ulishaji. */
    private double stockScreenBalance(int feedTypeId) {
        JsonNode rows = graphql(adminToken,
                "query { feedStockBalance { feedType { feedTypeId } quantityKg } }")
                .path("data").path("feedStockBalance");
        for (JsonNode row : rows) {
            if (row.path("feedType").path("feedTypeId").asInt() == feedTypeId) {
                return row.path("quantityKg").asDouble();
            }
        }
        return 0.0;
    }

    // =====================================================================

    @Nested
    @DisplayName("swichi inarudi pande zote mbili")
    class Reversible {

        /**
         * MZUNGUKO KAMILI. Hatua ya mwisho - kulisha TENA baada ya kurudisha -
         * ndiyo inayothibitisha kwamba kuzima hakukuacha alama yoyote nyuma.
         */
        @Test
        @DisplayName("kulisha -> kuzima -> kukataliwa -> kurudisha -> kulisha tena")
        void deactivationIsFullyReversible() {
            int id = createFeedType("PELLET_TOGGLE", 0, 12);
            purchase(id, 50);

            assertThat(graphqlErrorCode(feed(id, 5))).isNull();

            assertThat(graphqlErrorCode(setActive(id, false))).isNull();
            assertThat(graphqlErrorCode(feed(id, 5))).isEqualTo("VALIDATION_ERROR");

            assertThat(graphqlErrorCode(setActive(id, true))).isNull();
            assertThat(graphqlErrorCode(feed(id, 5)))
                    .as("iliyorudishwa inalishwa kama haikuwahi kuzimwa")
                    .isNull();

            // Kilo zimeondoka MARA MBILI tu: ulishaji wa katikati ulikataliwa,
            // hivyo haukugusa leja. 50 - 5 - 5 = 40.
            assertThat(remainingKg(id)).isEqualTo(40.0);
        }

        /**
         * KUZIMA HAKUKATALIWI, hata pale kikwazo kingekuwa na kila sababu:
         * kilo ghalani NA mzunguko unaoitegemea. Hii ndiyo tofauti kati ya
         * onyo na kikwazo, ikiandikwa kama jaribio.
         */
        @Test
        @DisplayName("kuzima kunapita hata ikiwa na stoo na mzunguko tegemezi")
        void deactivationIsNeverBlocked() {
            int id = createFeedType("PELLET_ONLY", 0, 2);
            purchase(id, 80);

            // Onyo lina namba ZOTE MBILI kubwa...
            assertThat(remainingKg(id)).isEqualTo(80.0);
            assertThat(dependentCycles(id)).isEqualTo(1);

            // ...na kitendo bado kinapita.
            JsonNode res = setActive(id, false);
            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("setFeedTypeActive").path("active").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("iliyozimwa inakataliwa wakati wa KUANDIKA, kwa jina lake")
        void inactiveTypeIsRejectedAtWriteTime() {
            int id = createFeedType("PELLET_OFF", 0, 12);
            purchase(id, 50);
            setActive(id, false);

            JsonNode res = feed(id, 5);

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("PELLET_OFF").contains("haitumiki");

            // Ulishaji uliokataliwa HAUKUGUSA leja - salio bado ni la ununuzi
            // peke yake.
            assertThat(remainingKg(id)).isEqualTo(50.0);
        }

        /**
         * Aina iliyozimwa haitolewi na feedTypesForCycle, hivyo mteja
         * mwaminifu hangeweza kuichagua - ukaguzi wa logFeeding hapo juu ni
         * kwa asiye mwaminifu. Zote mbili zinahitajika; hii inashikilia ile
         * ya kwanza.
         */
        @Test
        @DisplayName("iliyozimwa haitokei tena kwenye orodha ya kuchagua ya mzunguko")
        void inactiveTypeLeavesTheCyclePicker() {
            int id = createFeedType("PELLET_PICKER", 0, 12);
            String query = "query { feedTypesForCycle(cycleId: " + cycleA
                    + ") { noSuitableFeed feedTypes { feedType { feedTypeId } suitability } } }";

            JsonNode before = graphql(workerToken, query).path("data").path("feedTypesForCycle");
            assertThat(before.path("feedTypes")).hasSize(1);
            assertThat(before.path("noSuitableFeed").asBoolean()).isFalse();

            setActive(id, false);

            JsonNode after = graphql(workerToken, query).path("data").path("feedTypesForCycle");
            assertThat(after.path("feedTypes")).isEmpty();
            // Katalogi imebaki bila kinachomfaa - ni pengo halisi, na
            // bendera yake inaisema.
            assertThat(after.path("noSuitableFeed").asBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("onyo: kilo zilizobaki")
    class RemainingStock {

        @Test
        @DisplayName("ni jumla ile ile ya leja inayosomwa na skrini ya ulishaji")
        void matchesTheFeedingScreen() {
            int id = createFeedType("PELLET_STOCK", 0, 12);
            purchase(id, 50);
            purchase(id, 30);
            feed(id, 12);

            // 50 + 30 - 12
            assertThat(remainingKg(id)).isEqualTo(68.0);
            assertThat(remainingKg(id))
                    .as("namba mbili za kitu kimoja lazima ziwe moja")
                    .isEqualTo(stockScreenBalance(id));
        }

        @Test
        @DisplayName("aina isiyowahi kuhamishwa ni SIFURI, si hitilafu wala null")
        void isZeroForAnUntouchedType() {
            int id = createFeedType("PELLET_NEVER_BOUGHT", 0, 12);

            JsonNode impact = impact(id);

            assertThat(impact.path("remainingKg").isNull()).isFalse();
            assertThat(impact.path("remainingKg").asDouble()).isEqualTo(0.0);
        }

        /**
         * Kilo za aina NYINGINE hazionekani hapa. Ni sababu ile ile
         * iliyogawa `feedStockBalance` kwa aina: chakula hakibadilishani,
         * hivyo onyo linaloonyesha jumla ya ghala lingedanganya.
         */
        @Test
        @DisplayName("linahesabu aina HII pekee")
        void countsOnlyItsOwnType() {
            int mine = createFeedType("PELLET_MINE", 0, 12);
            int other = createFeedType("PELLET_OTHER", 0, 12);
            purchase(mine, 25);
            purchase(other, 90);

            assertThat(remainingKg(mine)).isEqualTo(25.0);
            assertThat(remainingKg(other)).isEqualTo(90.0);
        }
    }

    /**
     * Hesabu yenye utata halisi. Mzunguko ni TEGEMEZI ikiwa aina hii ni
     * EXACT yake NA hakuna nyingine inayotumika iliyo EXACT au SAFE_LOWER
     * kwake - hali nne zilizojaribiwa hapa chini moja baada ya nyingine.
     */
    @Nested
    @DisplayName("onyo: mizunguko tegemezi")
    class DependentCycles {

        @Test
        @DisplayName("aina ya PEKEE inayomfaa mzunguko inahesabiwa")
        void countsTheSoleExactMatch() {
            int sole = createFeedType("SOLE_EXACT", 0, 2);

            assertThat(dependentCycles(sole)).isEqualTo(1);
        }

        @Test
        @DisplayName("EXACT nyingine inaondoa utegemezi")
        void anotherExactRemovesTheDependency() {
            int sole = createFeedType("EXACT_A", 0, 2);
            assertThat(dependentCycles(sole)).isEqualTo(1);

            createFeedType("EXACT_B", 1, 3);

            assertThat(dependentCycles(sole)).isZero();
        }

        /**
         * SAFE_LOWER INATOSHA kuondoa utegemezi, na hii ndiyo hali rahisi
         * kuikosea. Chakula cha samaki wadogo kuliko hawa kinaliwa na
         * wakubwa - punje ndogo kuliko inavyohitajika si hatari - hivyo
         * mzunguko wenye chaguo hilo HAUJAACHWA bila kitu.
         */
        @Test
        @DisplayName("SAFE_LOWER pia inaondoa utegemezi, si EXACT pekee")
        void safeLowerAlsoRemovesTheDependency() {
            int sole = createFeedType("EXACT_C", 0, 2);
            assertThat(dependentCycles(sole)).isEqualTo(1);

            // [0, 0]: dirisha lililokwisha pita - SAFE_LOWER kwa miezi 1.
            createFeedType("FRY_ONLY", 0, 0);

            assertThat(dependentCycles(sole)).isZero();
        }

        /**
         * UNSAFE_HIGHER HAIOKOI: ni chakula cha samaki wakubwa kuliko hawa,
         * ambacho feedTypesForCycle haikirudishi kabisa na logFeeding
         * inakikataa. Kingehesabiwa kama mbadala, onyo lingenyamaza kwa
         * mzunguko ambao KWELI unaachwa bila chakula.
         */
        @Test
        @DisplayName("UNSAFE_HIGHER si mbadala - utegemezi unabaki")
        void unsafeHigherIsNotAnAlternative() {
            int sole = createFeedType("EXACT_D", 0, 2);
            createFeedType("TOO_BIG", 5, 9);

            assertThat(dependentCycles(sole)).isEqualTo(1);
        }

        /**
         * Aina iliyozimwa haiwezi kuwa mbadala wa mtu: haiko kwenye orodha
         * ya kuchagua wala haipiti kwenye logFeeding.
         */
        @Test
        @DisplayName("aina iliyozimwa si mbadala")
        void inactiveTypeIsNotAnAlternative() {
            int sole = createFeedType("EXACT_E", 0, 2);
            int spare = createFeedType("SPARE", 0, 2);
            assertThat(dependentCycles(sole)).isZero();

            setActive(spare, false);

            assertThat(dependentCycles(sole)).isEqualTo(1);
        }

        @Test
        @DisplayName("aina isiyo EXACT kwa mzunguko wowote haina tegemezi")
        void aTypeThatFitsNobodyHasNoDependents() {
            // Miezi 5-9 dhidi ya mzunguko wa miezi 1: UNSAFE_HIGHER.
            int tooBig = createFeedType("FUTURE_FEED", 5, 9);
            // Dirisha lililopita: SAFE_LOWER, si EXACT.
            int tooSmall = createFeedType("PAST_FEED", 0, 0);

            assertThat(dependentCycles(tooBig)).isZero();
            assertThat(dependentCycles(tooSmall)).isZero();
        }

        /**
         * Mzunguko ULIOVUNWA hauhesabiwi: hesabu ni ya samaki walio ndani ya
         * maji leo, si ya historia.
         */
        @Test
        @DisplayName("mizunguko isiyoendelea haihesabiwi")
        void ignoresCyclesThatAreNotActive() {
            int sole = createFeedType("EXACT_F", 0, 2);
            assertThat(dependentCycles(sole)).isEqualTo(1);

            inTx(() -> {
                Cycle cycle = cycleRepository.findById(cycleA).orElseThrow();
                cycle.setStatus("HARVESTED");
                return cycleRepository.save(cycle);
            });

            assertThat(dependentCycles(sole)).isZero();
        }
    }

    @Nested
    @DisplayName("ruhusa na hali zisizo za kawaida")
    class Guards {

        /**
         * Onyo linahitaji ruhusa ya KITENDO chenyewe: `manage_feed_stock`.
         * Mwenye `log_feeding` pekee hapati lolote kati ya mawili - wala
         * kubadilisha swichi, wala kusoma namba zinazomshauri mtu kuibadili.
         */
        @Test
        @DisplayName("mwenye log_feeding pekee hapati swichi wala onyo")
        void bothRequireManageFeedStock() {
            int id = createFeedType("PELLET_GUARDED", 0, 12);

            assertThat(graphqlErrorCode(graphql(workerToken, setActiveMutation(id, false))))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(workerToken, impactQuery(id))))
                    .isEqualTo("FORBIDDEN");

            // Hakuna iliyopita.
            assertThat(remainingKg(id)).isEqualTo(0.0);
            assertThat(graphql(adminToken, setActiveMutation(id, true))
                    .path("data").path("setFeedTypeActive").path("active").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("onyo HALIANDIKI chochote - swichi inabaki ilivyokuwa")
        void impactIsReadOnly() {
            int id = createFeedType("PELLET_READONLY", 0, 2);
            purchase(id, 40);

            impact(id);
            impact(id);

            JsonNode row = graphql(adminToken, "query { feedTypes(activeOnly: false) "
                    + "{ feedTypeId active } }").path("data").path("feedTypes").get(0);
            assertThat(row.path("feedTypeId").asInt()).isEqualTo(id);
            assertThat(row.path("active").asBoolean()).isTrue();
            // Wala leja haijaguswa.
            assertThat(remainingKg(id)).isEqualTo(40.0);
        }

        @Test
        @DisplayName("aina isiyojulikana ni VALIDATION_ERROR, si jibu la sifuri")
        void unknownFeedTypeIsAnError() {
            JsonNode res = graphql(adminToken, impactQuery(999_999));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
        }

        /** Umri wa fixture ni sehemu ya kila hesabu hapo juu - ushahidi wake. */
        @Test
        @DisplayName("ushahidi: mzunguko wa fixture una miezi 1")
        void fixtureCycleAgeIsAsAssumed() {
            JsonNode res = graphql(workerToken,
                    "query { feedTypesForCycle(cycleId: " + cycleA + ") { cycleAgeMonths } }");

            assertThat(res.path("data").path("feedTypesForCycle").path("cycleAgeMonths").asInt())
                    .isEqualTo(CYCLE_AGE_MONTHS);
        }
    }
}
