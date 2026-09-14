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
import java.time.ZoneId;

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
 * SEHEMU YA PILI: MAVUNO NI MATUKIO (V25). closeCycle haipokei idadi
 * wala uzito tena - inajumlisha matukio ya SOLD/DIED/REMOVED. Majaribio
 * ya kufunga yanarekodi matukio KWANZA, kisha yanafunga, kisha
 * yanathibitisha jumla. Matukio yenyewe (uthibitisho wa kila uga,
 * ruhusa, ufichaji wa fedha) yana faili lao: HarvestEventsTest.
 *
 * SEHEMU YA TATU: kiwango cha kuishi. `survivalRateEstimate` ni MAKISIO
 * ya siku ya kuweka; `actualSurvivalRate` ni KILICHOTOKEA = (SOLD +
 * REMOVED) / vifaranga, kinachokokotolewa na database. DIED HAIMO.
 * Majaribio yanashikilia mambo mawili: kwamba hesabu ni sahihi, na
 * kwamba HAKUNA NJIA ya mteja kuipandikiza thamani yake mwenyewe - wala
 * idadi inayoizaa.
 */
@DisplayName("F - Mzunguko: kuweka, kutabiri, kufunga")
class CycleLifecycleTest extends IntegrationTest {

    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    /**
     * Tarehe ya kuweka ya majaribio ya kufunga. Ya MWAKA ULIOPITA kwa
     * makusudi: matukio ya mavuno hayawezi kuwa ya baadaye, hivyo tarehe
     * zote za mfano (Juni-Agosti 2025) lazima ziwe zimekwisha pita
     * saa yoyote majaribio yanapoendeshwa.
     */
    private static final String STOCKED = "2025-01-10";

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

    private static final String CYCLE_FIELDS = "{ cycleId stockingAgeMonths expectedHarvestDate status "
            + "survivalRateEstimate actualSurvivalRate actualHarvestDate harvestedCount totalWeightKg "
            + "mortalityCount totalRevenue fingerlingCost harvestNotes }";

    private JsonNode stock(String stockingDate, Integer stockingAgeMonths, int fingerlings) {
        String age = stockingAgeMonths == null ? "" : ", stockingAgeMonths: " + stockingAgeMonths;
        return graphql(adminToken, "mutation { createCycle(input: {unitId: " + unitA
                + ", speciesId: " + sixMonthSpecies + ", stockingDate: \"" + stockingDate
                + "\", fingerlingsCount: " + fingerlings + age + "}) " + CYCLE_FIELDS + " }");
    }

    private JsonNode stockOk(String stockingDate, Integer stockingAgeMonths) {
        JsonNode res = stock(stockingDate, stockingAgeMonths, 1000);
        assertThat(graphqlErrorCode(res)).isNull();
        return res.path("data").path("createCycle");
    }

    /** Mzunguko mpya wa vifaranga 1000, uliowekwa STOCKED. */
    private int stocked() {
        return stockOk(STOCKED, 0).path("cycleId").asInt();
    }

    private JsonNode record(int cycleId, String date, int fish, Double kg, String reason, Double amount) {
        String weight = kg == null ? "" : ", weightKg: " + kg;
        String sale = amount == null ? "" : ", saleAmount: " + amount;
        return graphql(adminToken, "mutation { recordHarvestEvent(cycleId: " + cycleId
                + ", eventDate: \"" + date + "\", fishCount: " + fish + weight
                + ", reason: \"" + reason + "\"" + sale + ") { harvestEventId } }");
    }

    private int recordOk(int cycleId, String date, int fish, Double kg, String reason, Double amount) {
        JsonNode res = record(cycleId, date, fish, kg, reason, amount);
        assertThat(graphqlErrorCode(res)).as("tukio la mfano: %s", res).isNull();
        return res.path("data").path("recordHarvestEvent").path("harvestEventId").asInt();
    }

    private int sell(int cycleId, String date, int fish, double kg, double amount) {
        return recordOk(cycleId, date, fish, kg, "SOLD", amount);
    }

    private int die(int cycleId, String date, int fish) {
        return recordOk(cycleId, date, fish, null, "DIED", null);
    }

    private int remove(int cycleId, String date, int fish, Double kg) {
        return recordOk(cycleId, date, fish, kg, "REMOVED", null);
    }

    private String closeMutation(int cycleId, String outcome, String date, String notes) {
        String notesArg = notes == null ? "" : ", notes: \"" + notes + "\"";
        return "mutation { closeCycle(cycleId: " + cycleId + ", outcome: \"" + outcome
                + "\", actualHarvestDate: \"" + date + "\"" + notesArg + ") " + CYCLE_FIELDS + " }";
    }

    private JsonNode close(int cycleId, String outcome, String date) {
        return graphql(adminToken, closeMutation(cycleId, outcome, date, null));
    }

    private JsonNode closedOk(int cycleId, String outcome, String date) {
        JsonNode res = close(cycleId, outcome, date);
        assertThat(graphqlErrorCode(res)).as("kufunga: %s", res).isNull();
        return res.path("data").path("closeCycle");
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
        JsonNode cycles = graphql(adminToken, "query { cycles " + CYCLE_FIELDS + " }")
                .path("data").path("cycles");
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

    /**
     * Gharama ya vifaranga (V25) - HIARI, na > 0 ikitolewa.
     */
    @Nested
    @DisplayName("gharama ya vifaranga")
    class FingerlingCost {

        private JsonNode stockWithCost(String cost) {
            return graphql(adminToken, "mutation { createCycle(input: {unitId: " + unitA
                    + ", speciesId: " + sixMonthSpecies + ", stockingDate: \"" + STOCKED
                    + "\", fingerlingsCount: 1000, fingerlingCost: " + cost + "}) "
                    + "{ cycleId fingerlingCost } }");
        }

        @Test
        @DisplayName("ikitolewa inahifadhiwa na kurudishwa")
        void storedWhenSent() {
            JsonNode res = stockWithCost("250000.50");

            assertThat(graphqlErrorCode(res)).isNull();
            int cycleId = res.path("data").path("createCycle").path("cycleId").asInt();
            assertThat(res.path("data").path("createCycle").path("fingerlingCost").asDouble())
                    .isEqualTo(250000.50);
            assertThat(cycleFromList(cycleId).path("fingerlingCost").asDouble()).isEqualTo(250000.50);
        }

        /** Mteja wa zamani asiyeituma - null = haikurekodiwa, si sifuri. */
        @Test
        @DisplayName("ikiachwa wazi ni null")
        void nullWhenOmitted() {
            assertThat(stockOk(STOCKED, 0).path("fingerlingCost").isNull()).isTrue();
        }

        @Test
        @DisplayName("sifuri na hasi zinakataliwa")
        void refusesZeroAndNegative() {
            assertThat(graphqlErrorCode(stockWithCost("0"))).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(stockWithCost("-100"))).isEqualTo("VALIDATION_ERROR");
            // 0.004 ni chanya, lakini NUMERIC(14,2) ingeigeuza 0.00.
            assertThat(graphqlErrorCode(stockWithCost("0.004"))).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Nested
    @DisplayName("kufunga mzunguko")
    class Closing {

        /**
         * JUMLA ZOTE NNE kutoka matukio manne ya aina tatu:
         *
         *   SOLD    600 samaki, 300.0 kg, 1,800,000
         *   SOLD    200 samaki, 100.0 kg,   600,000
         *   REMOVED  50 samaki,  20.5 kg
         *   DIED    100 samaki
         *
         *   harvestedCount = 600 + 200 + 50 = 850   (DIED haimo)
         *   totalWeightKg  = 300 + 100 + 20.5 = 420.5
         *   mortalityCount = 100
         *   totalRevenue   = 2,400,000
         *   kuishi         = 850 / 1000 = 0.85
         */
        @Test
        @DisplayName("HARVESTED inajumlisha matukio: idadi, uzito, vifo, mapato, kuishi")
        void harvestClosesTheCycle() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 600, 300.0, 1_800_000);
            sell(cycleId, "2025-07-08", 200, 100.0, 600_000);
            remove(cycleId, "2025-07-10", 50, 20.5);
            die(cycleId, "2025-06-20", 100);

            JsonNode res = graphql(adminToken, closeMutation(cycleId, "HARVESTED", "2025-07-15",
                    "Mavuno mazuri"));

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode closed = res.path("data").path("closeCycle");
            assertThat(closed.path("status").asText()).isEqualTo("HARVESTED");
            assertThat(closed.path("actualHarvestDate").asText()).isEqualTo("2025-07-15");
            assertThat(closed.path("harvestedCount").asInt()).isEqualTo(850);
            assertThat(closed.path("totalWeightKg").asDouble()).isEqualTo(420.5);
            assertThat(closed.path("mortalityCount").asInt()).isEqualTo(100);
            assertThat(closed.path("totalRevenue").asDouble()).isEqualTo(2_400_000.0);
            assertThat(closed.path("harvestNotes").asText()).isEqualTo("Mavuno mazuri");
            // 850 / 1000, ikikokotolewa na database - si na mwombaji.
            assertThat(closed.path("actualSurvivalRate").asDouble()).isEqualTo(0.85);

            // Na kila kitu kimehifadhiwa, si kwenye jibu la mutation pekee.
            JsonNode stored = cycleFromList(cycleId);
            assertThat(stored.path("status").asText()).isEqualTo("HARVESTED");
            assertThat(stored.path("harvestedCount").asInt()).isEqualTo(850);
            assertThat(stored.path("mortalityCount").asInt()).isEqualTo(100);
            assertThat(stored.path("totalRevenue").asDouble()).isEqualTo(2_400_000.0);
            assertThat(stored.path("actualSurvivalRate").asDouble()).isEqualTo(0.85);
        }

        /** FAILED bila tukio lolote: sifuri HALISI kila mahali, si null. */
        @Test
        @DisplayName("FAILED bila matukio inatoa sifuri kila mahali")
        void failedAcceptsZeroes() {
            int cycleId = stocked();

            JsonNode closed = closedOk(cycleId, "FAILED", "2025-03-01");

            assertThat(closed.path("status").asText()).isEqualTo("FAILED");
            assertThat(closed.path("harvestedCount").asInt()).isZero();
            assertThat(closed.path("totalWeightKg").asDouble()).isZero();
            assertThat(closed.path("mortalityCount").asInt()).isZero();
            assertThat(closed.path("totalRevenue").asDouble()).isZero();
            // Sifuri iliyovunwa kwa 1000 iliyowekwa = kuishi 0.0. Ni jibu
            // sahihi, si "hakijulikani".
            assertThat(closed.path("actualSurvivalRate").isNull()).isFalse();
            assertThat(closed.path("actualSurvivalRate").asDouble()).isZero();
        }

        /**
         * FAILED yenye mauzo YA SEHEMU ni halali: samaki wengi wamekufa,
         * waliobaki wameuzwa. Kuikataa kungepoteza kilo zilizookolewa.
         */
        @Test
        @DisplayName("FAILED inakubali mavuno ya sehemu")
        void failedAcceptsPartialHarvest() {
            int cycleId = stocked();
            die(cycleId, "2025-02-20", 800);
            sell(cycleId, "2025-02-25", 120, 30.0, 90_000);

            JsonNode closed = closedOk(cycleId, "FAILED", "2025-03-01");

            assertThat(closed.path("actualSurvivalRate").asDouble()).isEqualTo(0.12);
            assertThat(closed.path("mortalityCount").asInt()).isEqualTo(800);
            assertThat(closed.path("totalRevenue").asDouble()).isEqualTo(90_000.0);
        }

        /**
         * HARVESTED bila samaki HAI hata mmoja - hakuna tukio kabisa, AU
         * vifo peke yake - si mavuno. Hiyo ni FAILED.
         */
        @Test
        @DisplayName("HARVESTED bila samaki waliotoka hai inakataliwa - hiyo ni FAILED")
        void harvestedRejectsZeroes() {
            int cycleId = stocked();

            // Hakuna tukio lolote.
            assertThat(graphqlErrorCode(close(cycleId, "HARVESTED", "2025-07-15")))
                    .isEqualTo("VALIDATION_ERROR");

            // Vifo peke yake: samaki wametoka, lakini hakuna aliyeishi.
            die(cycleId, "2025-06-01", 900);
            assertThat(graphqlErrorCode(close(cycleId, "HARVESTED", "2025-07-15")))
                    .isEqualTo("VALIDATION_ERROR");

            // Bado uko wazi baada ya majaribio mawili yaliyokataliwa.
            assertThat(cycleFromList(cycleId).path("status").asText()).isEqualTo("ACTIVE");
        }

        /** REMOVED peke yake inatosha: samaki hai, hata bila kuuzwa wala kupimwa. */
        @Test
        @DisplayName("HARVESTED kwa REMOVED pekee (bila uzito) inakubalika")
        void removedAloneIsAHarvest() {
            int cycleId = stocked();
            remove(cycleId, "2025-07-01", 400, null);

            JsonNode closed = closedOk(cycleId, "HARVESTED", "2025-07-15");

            assertThat(closed.path("harvestedCount").asInt()).isEqualTo(400);
            assertThat(closed.path("totalWeightKg").asDouble()).isZero();
            assertThat(closed.path("totalRevenue").asDouble()).isZero();
            assertThat(closed.path("actualSurvivalRate").asDouble()).isEqualTo(0.40);
        }

        @Test
        @DisplayName("matokeo yasiyojulikana - na 'ACTIVE' - yanakataliwa")
        void refusesUnknownOutcome() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 800, 400.0, 1_000_000);

            assertThat(graphqlErrorCode(close(cycleId, "SOLD", "2025-07-15")))
                    .isEqualTo("VALIDATION_ERROR");
            // 'ACTIVE' ni hali halali ya mzunguko, lakini si ya KUFUNGA.
            assertThat(graphqlErrorCode(close(cycleId, "ACTIVE", "2025-07-15")))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("tarehe ya mavuno kabla ya kuweka inakataliwa")
        void refusesHarvestBeforeStocking() {
            int cycleId = stocked();

            assertThat(graphqlErrorCode(close(cycleId, "FAILED", "2024-12-31")))
                    .isEqualTo("VALIDATION_ERROR");
        }

        /** Bwawa lililofungwa tarehe 15 haliwezi kuwa na mauzo ya tarehe 20. */
        @Test
        @DisplayName("tarehe ya kufunga kabla ya tukio la mwisho inakataliwa")
        void refusesCloseBeforeLastEvent() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 500, 250.0, 700_000);
            sell(cycleId, "2025-07-20", 300, 150.0, 400_000);

            JsonNode res = close(cycleId, "HARVESTED", "2025-07-15");

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("2025-07-20");
            // Siku ile ile ya tukio la mwisho INAKUBALIKA.
            assertThat(graphqlErrorCode(close(cycleId, "HARVESTED", "2025-07-20"))).isNull();
        }

        /**
         * Tukio lililofutwa (soft-delete) HALIMO kwenye jumla - ndiyo maana
         * ya kulifuta. Ndiyo njia ya kurekebisha idadi iliyokosewa.
         */
        @Test
        @DisplayName("matukio yaliyofutwa hayamo kwenye jumla ya kufunga")
        void deletedEventsAreNotCounted() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 500, 250.0, 700_000);
            int mistake = sell(cycleId, "2025-07-02", 5000, 2500.0, 7_000_000);
            assertThat(graphqlErrorCode(graphql(adminToken,
                    "mutation { deleteHarvestEvent(harvestEventId: " + mistake + ") }"))).isNull();

            JsonNode closed = closedOk(cycleId, "HARVESTED", "2025-07-15");

            assertThat(closed.path("harvestedCount").asInt()).isEqualTo(500);
            assertThat(closed.path("totalWeightKg").asDouble()).isEqualTo(250.0);
            assertThat(closed.path("totalRevenue").asDouble()).isEqualTo(700_000.0);
            assertThat(closed.path("actualSurvivalRate").asDouble()).isEqualTo(0.50);
        }

        /**
         * Ulinzi wa NAMBA, si ukamilifu wa kinadharia: ombi la pili
         * lingeandika jumla mpya juu ya zilizorekodiwa, na kuishi
         * kungebadilika nayo kimyakimya.
         */
        @Test
        @DisplayName("kufunga uliokwisha fungwa kunakataliwa, na data ya kwanza inabaki")
        void refusesToCloseAClosedCycle() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 850, 420.5, 2_000_000);
            closedOk(cycleId, "HARVESTED", "2025-07-15");

            JsonNode res = close(cycleId, "HARVESTED", "2025-08-20");

            assertThat(graphqlErrorCode(res)).isEqualTo("CYCLE_ALREADY_CLOSED");

            // Mavuno ya KWANZA hayajaguswa - ndiyo maana ya kikwazo.
            JsonNode stored = cycleFromList(cycleId);
            assertThat(stored.path("harvestedCount").asInt()).isEqualTo(850);
            assertThat(stored.path("actualHarvestDate").asText()).isEqualTo("2025-07-15");
            assertThat(stored.path("actualSurvivalRate").asDouble()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("mzunguko uliofeli nao hauwezi kufungwa tena")
        void refusesToCloseAFailedCycle() {
            int cycleId = stocked();
            closedOk(cycleId, "FAILED", "2025-03-01");

            assertThat(graphqlErrorCode(close(cycleId, "HARVESTED", "2025-07-15")))
                    .isEqualTo("CYCLE_ALREADY_CLOSED");
        }

        @Test
        @DisplayName("kufunga kunahitaji edit_cycle - ile ile ya kuweka")
        void closingRequiresEditCycle() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 800, 400.0, 1_000_000);

            JsonNode res = graphql(workerToken,
                    closeMutation(cycleId, "HARVESTED", "2025-07-15", null));

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
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 800, 400.0, 1_000_000);

            closedOk(cycleId, "HARVESTED", "2025-07-15");

            // cycleA ya fixture bado inaendelea kwenye tanki hili hili.
            assertThat(unitStatus(unitA)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("tanki linarudi IDLE mzunguko wake wa mwisho ukifungwa")
        void unitIsFreedByTheLastClose() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 800, 400.0, 1_000_000);
            closedOk(cycleId, "HARVESTED", "2025-07-15");

            // cycleA ya fixture iliwekwa MWEZI MMOJA uliopita, hivyo tukio na
            // tarehe yake ya kufunga lazima viwe baada ya hapo - LEO YA EAT,
            // si tarehe ya mfano iliyoandikwa kwa mkono.
            String today = LocalDate.now(EAT).toString();
            sell(cycleA, today, 400, 200.0, 500_000);
            closedOk(cycleA, "HARVESTED", today);

            assertThat(unitStatus(unitA)).isEqualTo("IDLE");
        }

        @Test
        @DisplayName("mzunguko wa shamba jingine hauwezi kufungwa")
        void refusesACycleFromAnotherFarm() {
            int cycleId = stocked();

            // workerB ni wa shamba B; hata angekuwa na edit_cycle, mzunguko
            // huu si wake.
            JsonNode res = graphql(workerBToken,
                    closeMutation(cycleId, "FAILED", "2025-07-15", null));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
        }
    }

    /**
     * KIWANGO CHA KUISHI: makisio na matokeo ni vitu VIWILI, na hakuna
     * mtu anayeandika la pili - wala idadi inayolizaa.
     */
    @Nested
    @DisplayName("kuishi: makisio dhidi ya matokeo")
    class SurvivalRate {

        @Test
        @DisplayName("kabla ya kufungwa, kilichotokea hakijulikani (null) - si sifuri")
        void actualIsNullWhileOpen() {
            JsonNode cycle = stockOk(STOCKED, 0);

            assertThat(cycle.path("actualSurvivalRate").isNull()).isTrue();
            assertThat(cycle.path("harvestedCount").isNull()).isTrue();
            assertThat(cycle.path("mortalityCount").isNull()).isTrue();
            assertThat(cycle.path("totalRevenue").isNull()).isTrue();
            assertThat(cycle.path("actualHarvestDate").isNull()).isTrue();
        }

        /**
         * Matukio YANAYOENDELEA hayabadilishi mzunguko ulio wazi: jumla
         * zinaandikwa siku ya kufunga pekee. Mpaka hapo, orodha ya
         * `harvestEvents` ndiyo inayoonyesha kinachoendelea.
         */
        @Test
        @DisplayName("matukio ya mzunguko ulio wazi hayaandiki jumla kabla ya kufunga")
        void eventsDoNotTouchTotalsWhileOpen() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 500, 250.0, 700_000);

            JsonNode open = cycleFromList(cycleId);
            assertThat(open.path("status").asText()).isEqualTo("ACTIVE");
            assertThat(open.path("harvestedCount").isNull()).isTrue();
            assertThat(open.path("actualSurvivalRate").isNull()).isTrue();
        }

        /**
         * MAKISIO HAYAGUSWI na kufunga. Yalikuwa 0.85 siku ya kuweka na
         * yanabaki 0.85 hata mavuno yakiwa 0.60 - kulinganisha ndiyo maana
         * ya kuyahifadhi.
         */
        @Test
        @DisplayName("makisio yanabaki yalivyo, hata matokeo yakiwa tofauti")
        void estimateStaysSeparateFromActual() {
            JsonNode created = stockOk(STOCKED, 0);
            int cycleId = created.path("cycleId").asInt();
            assertThat(created.path("survivalRateEstimate").asDouble()).isEqualTo(0.85);
            sell(cycleId, "2025-07-01", 600, 300.0, 900_000);

            JsonNode closed = closedOk(cycleId, "HARVESTED", "2025-07-15");

            assertThat(closed.path("actualSurvivalRate").asDouble()).isEqualTo(0.60);
            assertThat(closed.path("survivalRateEstimate").asDouble())
                    .as("makisio ya siku ya kuweka hayabadilishwi na mavuno")
                    .isEqualTo(0.85);
        }

        /**
         * UAMUZI WA MSINGI WA V25: kuishi = samaki waliotoka WAKIWA HAI.
         * Vifo 300 vinaonekana kwenye mortalityCount, lakini HAVIMO kwenye
         * kuishi - vinginevyo bwawa lenye vifo vingi lingeonekana
         * limefanikiwa.
         *
         *   (500 SOLD + 100 REMOVED) / 1000 = 0.60, si (900) / 1000 = 0.90
         */
        @Test
        @DisplayName("DIED haimo kwenye kuishi - inaenda mortalityCount pekee")
        void diedIsExcludedFromSurvival() {
            int cycleId = stocked();
            die(cycleId, "2025-05-01", 300);
            sell(cycleId, "2025-07-01", 500, 250.0, 700_000);
            remove(cycleId, "2025-07-02", 100, 45.0);

            JsonNode closed = closedOk(cycleId, "HARVESTED", "2025-07-15");

            assertThat(closed.path("harvestedCount").asInt()).isEqualTo(600);
            assertThat(closed.path("mortalityCount").asInt()).isEqualTo(300);
            // Uzito wa vifo haujulikani na hauhesabiwi: 250 + 45 pekee.
            assertThat(closed.path("totalWeightKg").asDouble()).isEqualTo(295.0);
            assertThat(closed.path("actualSurvivalRate").asDouble()).isEqualTo(0.60);
        }

        /**
         * Kuhesabu samaki si sayansi kamili: vifaranga "1000" vinaweza
         * kutoa samaki 1100. HAIKATALIWI (V19 iliruhusu kuishi > 1.0 kwa
         * sababu hiyo hiyo).
         */
        @Test
        @DisplayName("samaki zaidi ya waliowekwa wanakubalika - kuishi zaidi ya 1.0")
        void countsAboveStockedAreAllowed() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 1100, 550.0, 1_500_000);

            JsonNode closed = closedOk(cycleId, "HARVESTED", "2025-07-15");

            assertThat(closed.path("actualSurvivalRate").asDouble()).isEqualTo(1.1);
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
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 500, 250.0, 700_000);

            JsonNode res = graphql(adminToken, "mutation { closeCycle(cycleId: " + cycleId
                    + ", outcome: \"HARVESTED\", actualHarvestDate: \"2025-07-15\""
                    + ", actualSurvivalRate: 0.99) { cycleId } }");

            assertThat(res.path("errors").isArray() && !res.path("errors").isEmpty())
                    .as("uga usiokuwepo kwenye schema unakataliwa na GraphQL yenyewe")
                    .isTrue();

            // Wala hakuna kilichofungwa kwa bahati mbaya.
            assertThat(cycleFromList(cycleId).path("status").asText()).isEqualTo("ACTIVE");
        }

        /**
         * MABADILIKO YA MKATABA YA V25, yakiandikwa kama jaribio: idadi na
         * uzito vilikuwa LAZIMA kwenye closeCycle, sasa HAVIPO. Mteja
         * anayevituma bado anapata kosa la schema - si namba zake
         * kukubaliwa kimyakimya juu ya jumla ya matukio.
         */
        @Test
        @DisplayName("idadi na uzito haviwezi kutumwa kwa closeCycle tena")
        void countsCannotBeSuppliedAtClose() {
            int cycleId = stocked();
            sell(cycleId, "2025-07-01", 500, 250.0, 700_000);

            JsonNode res = graphql(adminToken, "mutation { closeCycle(cycleId: " + cycleId
                    + ", outcome: \"HARVESTED\", actualHarvestDate: \"2025-07-15\""
                    + ", harvestedCount: 990, totalWeightKg: 900) { cycleId } }");

            assertThat(res.path("errors").isArray() && !res.path("errors").isEmpty()).isTrue();
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
            assertThat(stillOpen.path("mortalityCount").isNull()).isTrue();
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
            int cycleId = stocked();
            sell(cycleId, "2025-08-01", 800, 400.0, 1_000_000);

            JsonNode closed = closedOk(cycleId, "HARVESTED", "2025-08-20");

            assertThat(closed.path("expectedHarvestDate").asText()).isEqualTo("2025-07-10");
            assertThat(closed.path("actualHarvestDate").asText()).isEqualTo("2025-08-20");
        }

        /**
         * TIE-IN YA createDefaultTasks. Violezo vya kazi za kila siku
         * vinazalishwa wakati wa kuweka na HAVINA tarehe ya mwisho; ni
         * `cycle.status = 'ACTIVE'` pekee inayoviondoa kwenye kazi za leo.
         * Kufunga mzunguko ndiyo hatua inayoifanya - bila hiyo, mzunguko
         * uliovunwa ungeendelea kudai kulishwa milele.
         *
         * Na matukio ya mavuno PEKE YAKE hayafanyi hivyo: mzunguko wenye
         * mauzo ya sehemu bado una samaki majini wanaohitaji kulishwa.
         */
        @Test
        @DisplayName("kufunga kunakomesha kazi za kila siku za mzunguko huo")
        void closingStopsOutstandingDailyTasks() {
            int cycleId = stocked();
            String tasksQuery = "query { dailyTasks(cycleId: " + cycleId + ") { taskId done } }";

            // createDefaultTasks imezalisha violezo vitatu.
            assertThat(graphql(adminToken, tasksQuery).path("data").path("dailyTasks")).hasSize(3);

            sell(cycleId, "2025-07-01", 800, 400.0, 1_000_000);
            closedOk(cycleId, "HARVESTED", "2025-07-15");

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
