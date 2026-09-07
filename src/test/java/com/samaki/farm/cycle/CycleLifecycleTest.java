package com.samaki.farm.cycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.species.entity.Species;
import com.samaki.farm.species.repository.SpeciesRepository;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI F - MZUNGUKO KAMILI: kuweka (kwa umri wowote) hadi kufunga.
 *
 * Mzunguko ulikuwa na mwanzo bila mwisho. 'HARVESTED' na 'FAILED'
 * zilikuwa kwenye `cycles.status` tangu V1 na kwenye orodha ya hali
 * halali ya CycleService, lakini HAKUNA njia iliyofika huko: kila
 * mzunguko uliowahi kuwekwa ulibaki ACTIVE milele.
 *
 * =====================================================================
 * JAMBO LINALOJARIBIWA KWA BIDII ZAIDI HAPA NI LILE AMBALO HALITOKEI
 *
 * Hakuna kujifunga kiotomatiki. `expectedHarvestDate` ni UTABIRI, na
 * tarehe hiyo ikipita hakuna kinachobadilika - hakuna scheduler
 * inayoipitia, na hakuna hali inayogeuka. Jaribio
 * `passingTheExpectedDateChangesNothing` linaandika hilo kwa vitendo,
 * likitumia mzunguko ambao tarehe yake ya utabiri IMESHAPITA.
 *
 * Ni sheria ya kiuendeshaji, si ya kiufundi: samaki hawavunwi kwa
 * sababu kalenda imesema. Mzunguko unaojifunga wenyewe ungeacha
 * kuzalisha vikumbusho vya kulisha samaki ambao bado wako majini
 * (DailyTaskRepository.findOutstandingForFarm inachuja kwa
 * `cycle.status = 'ACTIVE'`), na mwendeshaji angegundua hilo kwa samaki
 * kufa njaa.
 * =====================================================================
 *
 * SEHEMU YA PILI: kiwango cha kuishi. `survivalRateEstimate` ni MAKISIO
 * ya siku ya kuweka; `actualSurvivalRate` ni KILICHOTOKEA,
 * kinachokokotolewa na database kutoka idadi mbili zilizorekodiwa.
 * Majaribio yanashikilia mambo mawili: kwamba hesabu ni sahihi, na
 * kwamba HAKUNA NJIA ya mteja kuipandikiza thamani yake mwenyewe.
 */
@DisplayName("F - Mzunguko: kuweka, kutabiri, kufunga")
class CycleLifecycleTest extends IntegrationTest {

    /**
     * Aina za samaki zinatoka V1 na zina `growth_months_avg` zao. Majaribio
     * ya hesabu yanahitaji namba INAYOJULIKANA, hivyo yanapanda aina yao
     * wenyewe badala ya kutegemea thamani ya seed - ambayo ikibadilika
     * ingevunja majaribio kwa sababu isiyohusiana nayo.
     */
    @Autowired private SpeciesRepository speciesRepository;

    /** Aina ya miezi 6 MIZIMA - ndiyo namba ya mifano yote hapa. */
    private int sixMonthSpecies;

    @BeforeEach
    void seedSpecies() {
        sixMonthSpecies = inTx(() -> {
            Species species = new Species();
            species.setName("Test Sato 6M");
            species.setGrowthMonthsAvg(new BigDecimal("6.0"));
            species.setAvgHarvestWeightKg(new BigDecimal("0.5"));
            return speciesRepository.save(species);
        }).getSpeciesId();
    }

    // --------------------------------------------------------- misaada

    private JsonNode stock(String stockingDate, Integer stockingAgeMonths, int fingerlings) {
        String age = stockingAgeMonths == null ? "" : ", stockingAgeMonths: " + stockingAgeMonths;
        return graphql(adminToken, "mutation { createCycle(input: {unitId: " + unitA
                + ", speciesId: " + sixMonthSpecies + ", stockingDate: \"" + stockingDate
                + "\", fingerlingsCount: " + fingerlings + age + "}) "
                + "{ cycleId stockingAgeMonths expectedHarvestDate status survivalRateEstimate "
                + "actualSurvivalRate actualHarvestDate harvestedCount totalWeightKg } }");
    }

    private JsonNode stockOk(String stockingDate, Integer stockingAgeMonths) {
        JsonNode res = stock(stockingDate, stockingAgeMonths, 1000);
        assertThat(graphqlErrorCode(res)).isNull();
        return res.path("data").path("createCycle");
    }

    private String closeMutation(int cycleId, String outcome, String date,
                                  int harvestedCount, double totalWeightKg, String notes) {
        String notesArg = notes == null ? "" : ", notes: \"" + notes + "\"";
        return "mutation { closeCycle(cycleId: " + cycleId + ", outcome: \"" + outcome
                + "\", actualHarvestDate: \"" + date + "\", harvestedCount: " + harvestedCount
                + ", totalWeightKg: " + totalWeightKg + notesArg + ") "
                + "{ cycleId status actualHarvestDate harvestedCount totalWeightKg "
                + "harvestNotes actualSurvivalRate survivalRateEstimate expectedHarvestDate } }";
    }

    private JsonNode close(int cycleId, String outcome, String date,
                            int harvestedCount, double totalWeightKg) {
        return graphql(adminToken, closeMutation(cycleId, outcome, date, harvestedCount,
                totalWeightKg, null));
    }

    /** Hali ya tanki kama inavyosomwa na ukurasa wa vitengo. */
    private String unitStatus(int unitId) {
        JsonNode units = graphql(adminToken, "query { productionUnits { unitId status } }")
                .path("data").path("productionUnits");
        for (JsonNode unit : units) {
            if (unit.path("unitId").asInt() == unitId) {
                return unit.path("status").asText();
            }
        }
        return null;
    }

    /** Mzunguko mmoja kama unavyosomwa na orodha ya shamba. */
    private JsonNode cycleFromList(int cycleId) {
        JsonNode cycles = graphql(adminToken, "query { cycles { cycleId status stockingAgeMonths "
                + "expectedHarvestDate actualHarvestDate survivalRateEstimate actualSurvivalRate "
                + "harvestedCount totalWeightKg harvestNotes } }").path("data").path("cycles");
        for (JsonNode cycle : cycles) {
            if (cycle.path("cycleId").asInt() == cycleId) {
                return cycle;
            }
        }
        return null;
    }

    // =====================================================================

    /**
     * Hesabu: stockingDate + (miezi 6 ya aina - umri wa kuweka).
     *
     * Namba zote hapa ni za aina ya miezi 6.0 mizima, hivyo hakuna sehemu
     * ya desimali inayoingilia - mantiki ya nusu-mwezi (D-7) ina jaribio
     * lake tofauti hapa chini.
     */
    @Nested
    @DisplayName("utabiri wa tarehe ya mavuno")
    class ExpectedHarvest {

        @Test
        @DisplayName("umri 0: miezi 6 kamili tangu kuwekwa (tabia ya zamani, bila mabadiliko)")
        void ageZeroGrowsFullTerm() {
            JsonNode cycle = stockOk("2026-01-10", 0);

            assertThat(cycle.path("stockingAgeMonths").asInt()).isZero();
            assertThat(cycle.path("expectedHarvestDate").asText()).isEqualTo("2026-07-10");
        }

        /**
         * Uga usiotumwa kabisa UNAPASWA kutoa jibu lile lile la umri 0 -
         * ndicho kinachofanya mabadiliko haya yasivunje mteja wa zamani.
         */
        @Test
        @DisplayName("umri ukiachwa wazi ni sawa na 0")
        void omittedAgeDefaultsToZero() {
            JsonNode cycle = stockOk("2026-01-10", null);

            assertThat(cycle.path("stockingAgeMonths").asInt()).isZero();
            assertThat(cycle.path("expectedHarvestDate").asText()).isEqualTo("2026-07-10");
        }

        @Test
        @DisplayName("umri 1: miezi 5 zilizobaki")
        void ageOneGrowsFiveMonths() {
            JsonNode cycle = stockOk("2026-01-10", 1);

            assertThat(cycle.path("stockingAgeMonths").asInt()).isEqualTo(1);
            assertThat(cycle.path("expectedHarvestDate").asText()).isEqualTo("2026-06-10");
        }

        /** Samaki wa mwezi wa 2 anayelenga mwezi wa 6 anavunwa miezi 4 mbele. */
        @Test
        @DisplayName("umri 2: miezi 4 zilizobaki")
        void ageTwoGrowsFourMonths() {
            JsonNode cycle = stockOk("2026-01-10", 2);

            assertThat(cycle.path("stockingAgeMonths").asInt()).isEqualTo(2);
            assertThat(cycle.path("expectedHarvestDate").asText()).isEqualTo("2026-05-10");
        }

        /**
         * Nusu-mwezi (D-7) inaendelea kufanya kazi baada ya kutoa umri wa
         * kuweka: 6.5 - 1 = miezi 5.5, na nusu inahesabiwa kwa SIKU za mwezi
         * halisi inamoangukia - Juni ina siku 30, hivyo +15.
         */
        @Test
        @DisplayName("nusu-mwezi ya aina inabaki sahihi baada ya kutoa umri")
        void fractionalGrowthStillWorks() {
            int halfMonthSpecies = inTx(() -> {
                Species species = new Species();
                species.setName("Test Kambale 6.5M");
                species.setGrowthMonthsAvg(new BigDecimal("6.5"));
                species.setAvgHarvestWeightKg(new BigDecimal("0.8"));
                return speciesRepository.save(species);
            }).getSpeciesId();

            JsonNode res = graphql(adminToken, "mutation { createCycle(input: {unitId: " + unitA
                    + ", speciesId: " + halfMonthSpecies + ", stockingDate: \"2026-01-10\""
                    + ", fingerlingsCount: 500, stockingAgeMonths: 1}) { expectedHarvestDate } }");

            // 2026-01-10 + miezi 5 = 2026-06-10; nusu ya Juni (siku 30) = +15.
            assertThat(res.path("data").path("createCycle").path("expectedHarvestDate").asText())
                    .isEqualTo("2026-06-25");
        }
    }

    @Nested
    @DisplayName("kikwazo cha umri wa kuweka")
    class StockingAgeGuard {

        @Test
        @DisplayName("umri hasi unakataliwa")
        void refusesNegativeAge() {
            JsonNode res = stock("2026-01-10", -1, 1000);

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
        }

        /**
         * Umri sawa na umri wa kuvunwa ungetoa muda uliobaki wa SIFURI -
         * tarehe ya mavuno siku ile ile ya kuweka.
         */
        @Test
        @DisplayName("umri sawa na umri wa kuvunwa unakataliwa")
        void refusesAgeEqualToHarvestAge() {
            JsonNode res = stock("2026-01-10", 6, 1000);

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            // Ujumbe unataja NAMBA ZOTE MBILI - ndio unaotumiwa na frontend.
            assertThat(graphqlMessage(res)).contains("6");
        }

        /**
         * Umri unaozidi ungetoa muda HASI, na `plusMonths(-1)` ingerudisha
         * tarehe ya mavuno ILIYOKWISHA PITA siku ya kuweka - utabiri usio na
         * maana ukionekana kama halali.
         */
        @Test
        @DisplayName("umri unaozidi umri wa kuvunwa unakataliwa")
        void refusesAgePastHarvestAge() {
            JsonNode res = stock("2026-01-10", 9, 1000);

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Nested
    @DisplayName("kufunga mzunguko")
    class Closing {

        @Test
        @DisplayName("HARVESTED inaweka hali, tarehe, mavuno na kuishi kulikokokotolewa")
        void harvestClosesTheCycle() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            JsonNode res = graphql(adminToken, closeMutation(cycleId, "HARVESTED", "2026-07-15",
                    850, 420.5, "Mavuno mazuri"));

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode closed = res.path("data").path("closeCycle");
            assertThat(closed.path("status").asText()).isEqualTo("HARVESTED");
            assertThat(closed.path("actualHarvestDate").asText()).isEqualTo("2026-07-15");
            assertThat(closed.path("harvestedCount").asInt()).isEqualTo(850);
            assertThat(closed.path("totalWeightKg").asDouble()).isEqualTo(420.5);
            assertThat(closed.path("harvestNotes").asText()).isEqualTo("Mavuno mazuri");
            // 850 / 1000, ikikokotolewa na database - si na mwombaji.
            assertThat(closed.path("actualSurvivalRate").asDouble()).isEqualTo(0.85);

            // Na kila kitu kimehifadhiwa, si kwenye jibu la mutation pekee.
            JsonNode stored = cycleFromList(cycleId);
            assertThat(stored.path("status").asText()).isEqualTo("HARVESTED");
            assertThat(stored.path("harvestedCount").asInt()).isEqualTo(850);
            assertThat(stored.path("actualSurvivalRate").asDouble()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("FAILED inakubali sifuri kwa idadi na uzito")
        void failedAcceptsZeroes() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            JsonNode res = close(cycleId, "FAILED", "2026-03-01", 0, 0);

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode closed = res.path("data").path("closeCycle");
            assertThat(closed.path("status").asText()).isEqualTo("FAILED");
            assertThat(closed.path("harvestedCount").asInt()).isZero();
            assertThat(closed.path("totalWeightKg").asDouble()).isZero();
            // Sifuri iliyovunwa kwa 1000 iliyowekwa = kuishi 0.0. Ni jibu
            // sahihi, si "hakijulikani".
            assertThat(closed.path("actualSurvivalRate").asDouble()).isZero();
        }

        /**
         * FAILED yenye mavuno YA SEHEMU ni halali: samaki wengi wamekufa,
         * waliobaki wamevunwa. Kuilazimisha kuwa sifuri kungepoteza kilo
         * zilizookolewa.
         */
        @Test
        @DisplayName("FAILED inakubali mavuno ya sehemu")
        void failedAcceptsPartialHarvest() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            JsonNode res = close(cycleId, "FAILED", "2026-03-01", 120, 30.0);

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("closeCycle").path("actualSurvivalRate").asDouble())
                    .isEqualTo(0.12);
        }

        @Test
        @DisplayName("HARVESTED yenye sifuri inakataliwa - hiyo ni FAILED")
        void harvestedRejectsZeroes() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            assertThat(graphqlErrorCode(close(cycleId, "HARVESTED", "2026-07-15", 0, 100)))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(close(cycleId, "HARVESTED", "2026-07-15", 800, 0)))
                    .isEqualTo("VALIDATION_ERROR");

            // Bado uko wazi baada ya majaribio mawili yaliyokataliwa.
            assertThat(cycleFromList(cycleId).path("status").asText()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("matokeo yasiyojulikana - na 'ACTIVE' - yanakataliwa")
        void refusesUnknownOutcome() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            assertThat(graphqlErrorCode(close(cycleId, "SOLD", "2026-07-15", 800, 400)))
                    .isEqualTo("VALIDATION_ERROR");
            // 'ACTIVE' ni hali halali ya mzunguko, lakini si ya KUFUNGA.
            assertThat(graphqlErrorCode(close(cycleId, "ACTIVE", "2026-07-15", 800, 400)))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("tarehe ya mavuno kabla ya kuweka inakataliwa")
        void refusesHarvestBeforeStocking() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            JsonNode res = close(cycleId, "HARVESTED", "2025-12-31", 800, 400);

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
        }

        /**
         * Ulinzi wa NAMBA, si ukamilifu wa kinadharia: ombi la pili
         * lingeandika mavuno mengine juu ya yaliyorekodiwa, na kuishi
         * kungebadilika nayo kimyakimya.
         */
        @Test
        @DisplayName("kufunga uliokwisha fungwa kunakataliwa, na data ya kwanza inabaki")
        void refusesToCloseAClosedCycle() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();
            close(cycleId, "HARVESTED", "2026-07-15", 850, 420.5);

            JsonNode res = close(cycleId, "HARVESTED", "2026-08-20", 100, 50);

            assertThat(graphqlErrorCode(res)).isEqualTo("CYCLE_ALREADY_CLOSED");

            // Mavuno ya KWANZA hayajaguswa - ndiyo maana ya kikwazo.
            JsonNode stored = cycleFromList(cycleId);
            assertThat(stored.path("harvestedCount").asInt()).isEqualTo(850);
            assertThat(stored.path("actualHarvestDate").asText()).isEqualTo("2026-07-15");
            assertThat(stored.path("actualSurvivalRate").asDouble()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("mzunguko uliofeli nao hauwezi kufungwa tena")
        void refusesToCloseAFailedCycle() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();
            close(cycleId, "FAILED", "2026-03-01", 0, 0);

            assertThat(graphqlErrorCode(close(cycleId, "HARVESTED", "2026-07-15", 800, 400)))
                    .isEqualTo("CYCLE_ALREADY_CLOSED");
        }

        @Test
        @DisplayName("kufunga kunahitaji edit_cycle - ile ile ya kuweka")
        void closingRequiresEditCycle() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            JsonNode res = graphql(workerToken,
                    closeMutation(cycleId, "HARVESTED", "2026-07-15", 800, 400, null));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(cycleFromList(cycleId).path("status").asText()).isEqualTo("ACTIVE");
        }

        /**
         * `create` inaweka tanki ACTIVE; bila hatua ya kinyume kila tanki
         * lililowahi kutumika lingebaki likionekana limekaliwa MILELE.
         *
         * Fixture ina mzunguko wake kwenye tanki hili (cycleA), hivyo
         * majaribio haya mawili yanaipima pande zote za ukaguzi wa
         * "bado kuna mwingine?".
         */
        @Test
        @DisplayName("tanki linabaki ACTIVE ikiwa mzunguko mwingine bado unaendelea")
        void unitStaysBusyWhileAnotherCycleRuns() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            close(cycleId, "HARVESTED", "2026-07-15", 800, 400);

            // cycleA ya fixture bado inaendelea kwenye tanki hili hili.
            assertThat(unitStatus(unitA)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("tanki linarudi IDLE mzunguko wake wa mwisho ukifungwa")
        void unitIsFreedByTheLastClose() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            assertThat(graphqlErrorCode(close(cycleId, "HARVESTED", "2026-07-15", 800, 400)))
                    .isNull();
            // cycleA ya fixture iliwekwa MWEZI MMOJA uliopita, hivyo tarehe
            // yake ya mavuno lazima iwe baada ya hapo - si tarehe ya mfano
            // iliyoandikwa kwa mkono.
            assertThat(graphqlErrorCode(
                    close(cycleA, "HARVESTED", LocalDate.now().toString(), 400, 200)))
                    .isNull();

            assertThat(unitStatus(unitA)).isEqualTo("IDLE");
        }

        @Test
        @DisplayName("mzunguko wa shamba jingine hauwezi kufungwa")
        void refusesACycleFromAnotherFarm() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            // workerB ni wa shamba B; hata angekuwa na edit_cycle, mzunguko
            // huu si wake.
            JsonNode res = graphql(workerBToken,
                    closeMutation(cycleId, "HARVESTED", "2026-07-15", 800, 400, null));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
        }
    }

    /**
     * KIWANGO CHA KUISHI: makisio na matokeo ni vitu VIWILI, na hakuna
     * mtu anayeandika la pili.
     */
    @Nested
    @DisplayName("kuishi: makisio dhidi ya matokeo")
    class SurvivalRate {

        @Test
        @DisplayName("kabla ya kufungwa, kilichotokea hakijulikani (null) - si sifuri")
        void actualIsNullWhileOpen() {
            JsonNode cycle = stockOk("2026-01-10", 0);

            assertThat(cycle.path("actualSurvivalRate").isNull()).isTrue();
            assertThat(cycle.path("harvestedCount").isNull()).isTrue();
            assertThat(cycle.path("actualHarvestDate").isNull()).isTrue();
        }

        /**
         * MAKISIO HAYAGUSWI na kufunga. Yalikuwa 0.85 siku ya kuweka na
         * yanabaki 0.85 hata mavuno yakiwa 0.60 - kulinganisha ndiyo maana
         * ya kuyahifadhi.
         */
        @Test
        @DisplayName("makisio yanabaki yalivyo, hata matokeo yakiwa tofauti")
        void estimateStaysSeparateFromActual() {
            JsonNode created = stockOk("2026-01-10", 0);
            int cycleId = created.path("cycleId").asInt();
            assertThat(created.path("survivalRateEstimate").asDouble()).isEqualTo(0.85);

            JsonNode closed = close(cycleId, "HARVESTED", "2026-07-15", 600, 300)
                    .path("data").path("closeCycle");

            assertThat(closed.path("actualSurvivalRate").asDouble()).isEqualTo(0.60);
            assertThat(closed.path("survivalRateEstimate").asDouble())
                    .as("makisio ya siku ya kuweka hayabadilishwi na mavuno")
                    .isEqualTo(0.85);
        }

        /**
         * HAKUNA NJIA YA KUIPANDIKIZA. Uga hauko kwenye mutation hata
         * kidogo, hivyo GraphQL yenyewe inakataa ombi linalojaribu -
         * hakuna ukaguzi wa service unaoweza kusahaulika. (Safu yenyewe ni
         * GENERATED ALWAYS kwenye V19, hivyo hata psql ingekataa.)
         */
        @Test
        @DisplayName("haiwezi kutumwa kama input - schema yenyewe inakataa")
        void cannotBeSuppliedAsInput() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();

            JsonNode res = graphql(adminToken, "mutation { closeCycle(cycleId: " + cycleId
                    + ", outcome: \"HARVESTED\", actualHarvestDate: \"2026-07-15\""
                    + ", harvestedCount: 500, totalWeightKg: 250"
                    + ", actualSurvivalRate: 0.99) { cycleId } }");

            assertThat(res.path("errors").isArray() && !res.path("errors").isEmpty())
                    .as("uga usiokuwepo kwenye schema unakataliwa na GraphQL yenyewe")
                    .isTrue();

            // Wala hakuna kilichofungwa kwa bahati mbaya.
            assertThat(cycleFromList(cycleId).path("status").asText()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("wala kwenye kuweka - CreateCycleInput haina uga wake")
        void cannotBeSuppliedAtStocking() {
            JsonNode res = graphql(adminToken, "mutation { createCycle(input: {unitId: " + unitA
                    + ", speciesId: " + sixMonthSpecies + ", stockingDate: \"2026-01-10\""
                    + ", fingerlingsCount: 1000, actualSurvivalRate: 0.99}) { cycleId } }");

            assertThat(res.path("errors").isArray() && !res.path("errors").isEmpty()).isTrue();
        }
    }

    /**
     * SEHEMU YENYE THAMANI KUBWA ZAIDI YA FAILI HII: uthibitisho wa kile
     * KISICHOTOKEA.
     */
    @Nested
    @DisplayName("hakuna kujifunga kiotomatiki")
    class NoAutoClose {

        /**
         * Mzunguko ambao tarehe yake ya utabiri IMESHAPITA kwa muda mrefu -
         * uliwekwa mwaka mmoja uliopita, ukilenga miezi 6 - unabaki ACTIVE,
         * bila tarehe ya mavuno na bila idadi.
         *
         * Jaribio hili likianza kufeli, maana yake ni kwamba mtu ameongeza
         * njia inayobadilisha hali kwa tarehe - na hiyo ndiyo hasa sheria
         * ambayo module hii imeundwa kuizuia.
         */
        @Test
        @DisplayName("tarehe ya utabiri kupita HAKUBADILISHI chochote")
        void passingTheExpectedDateChangesNothing() {
            String stockedLongAgo = LocalDate.now().minusMonths(12).toString();
            JsonNode created = stockOk(stockedLongAgo, 0);
            int cycleId = created.path("cycleId").asInt();

            LocalDate expected = LocalDate.parse(created.path("expectedHarvestDate").asText());
            assertThat(expected)
                    .as("mfano wenyewe unahitaji tarehe iliyokwisha pita")
                    .isBefore(LocalDate.now());

            // Kusoma tena, mara kadhaa, kwa njia zote mbili za kusoma.
            cycleFromList(cycleId);
            JsonNode stillOpen = cycleFromList(cycleId);

            assertThat(stillOpen.path("status").asText()).isEqualTo("ACTIVE");
            assertThat(stillOpen.path("actualHarvestDate").isNull()).isTrue();
            assertThat(stillOpen.path("harvestedCount").isNull()).isTrue();
            assertThat(stillOpen.path("actualSurvivalRate").isNull()).isTrue();
        }

        /**
         * Utabiri unabaki umeandikwa hata baada ya kufunga: unaonyesha
         * ILIYOTABIRIWA kando ya ILIYOTOKEA. Kufunga HAKUUFUTI wala
         * hakuubadilishi.
         */
        @Test
        @DisplayName("kufunga hakuubadilishi utabiri - vyote viwili vinabaki")
        void closingKeepsThePrediction() {
            JsonNode created = stockOk("2026-01-10", 0);
            int cycleId = created.path("cycleId").asInt();

            JsonNode closed = close(cycleId, "HARVESTED", "2026-08-20", 800, 400)
                    .path("data").path("closeCycle");

            assertThat(closed.path("expectedHarvestDate").asText()).isEqualTo("2026-07-10");
            assertThat(closed.path("actualHarvestDate").asText()).isEqualTo("2026-08-20");
        }

        /**
         * TIE-IN YA createDefaultTasks. Violezo vya kazi za kila siku
         * vinazalishwa wakati wa kuweka na HAVINA tarehe ya mwisho; ni
         * `cycle.status = 'ACTIVE'` pekee inayoviondoa kwenye kazi za leo.
         * Kufunga mzunguko ndiyo hatua inayoifanya - bila hiyo, mzunguko
         * uliovunwa ungeendelea kudai kulishwa milele.
         */
        @Test
        @DisplayName("kufunga kunakomesha kazi za kila siku za mzunguko huo")
        void closingStopsOutstandingDailyTasks() {
            int cycleId = stockOk("2026-01-10", 0).path("cycleId").asInt();
            String tasksQuery = "query { dailyTasks(cycleId: " + cycleId + ") { taskId done } }";

            // createDefaultTasks imezalisha violezo vitatu.
            assertThat(graphql(adminToken, tasksQuery).path("data").path("dailyTasks")).hasSize(3);

            close(cycleId, "HARVESTED", "2026-07-15", 800, 400);

            // Historia ya violezo inabaki inasomeka (si kufutwa)...
            JsonNode afterClose = graphql(adminToken, tasksQuery);
            assertThat(graphqlErrorCode(afterClose)).isNull();

            // ...lakini hazihesabiki tena kama kazi ZINAZOSUBIRI za shamba,
            // ambako vikumbusho vinazitoa.
            JsonNode outstanding = graphql(adminToken,
                    "query { cycles(status: \"ACTIVE\") { cycleId } }").path("data").path("cycles");
            for (JsonNode active : outstanding) {
                assertThat(active.path("cycleId").asInt()).isNotEqualTo(cycleId);
            }
        }
    }
}
