package com.samaki.farm.species;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.species.entity.Species;
import com.samaki.farm.species.repository.SpeciesRepository;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Katalogi ya AINA ZA SAMAKI - kuisimamia bila kugusa database.
 *
 * Hadi V20 katalogi ilikuwa ya kusoma pekee: aina mbili za V1 (Sato,
 * Kambale) na hakuna njia yoyote ya kuongeza ya tatu. Mkulima anayefuga
 * aina nyingine alikuwa amekwama - CreateCycleInput inadai speciesId,
 * na hakuna speciesId ya samaki wake.
 *
 * VITU VIWILI VINAELEZA MAJARIBIO MENGI HAPA:
 *
 *  1. `species` HAINA farm_id. Aina anayoiandika mtu mmoja inaonekana kwa
 *     MASHAMBA YOTE - ndiyo maana ruhusa ni `manage_species` (OWNER/
 *     FARM_MANAGER), si `view_dashboard` ya kusoma inayoshikwa na kila
 *     mtu ikiwemo VIEWER.
 *
 *  2. `growth_months_avg` ni NUMERIC(4,1) na ndiyo INAYOKOKOTOA
 *     expectedHarvestDate ya kila mzunguko wa aina hii. Nusu-mwezi ni
 *     thamani halali, na kuikata ni hitilafu iliyowahi kutokea kwenye
 *     repo hii (D-7). Majaribio ya "usahihi" hapa chini yanaibana.
 */
@DisplayName("Katalogi ya aina za samaki")
class SpeciesCatalogTest extends IntegrationTest {

    private static final String SPECIES_QUERY =
            "query { species { speciesId name growthMonthsAvg avgHarvestWeightKg } }";

    /**
     * Ushahidi wa soft-delete unahitaji kuandika `is_deleted` MOJA KWA
     * MOJA - hali ambayo API haina njia ya kuifikia (hakuna
     * deleteSpecies), lakini ambayo database inaweza kuwa nayo.
     */
    @Autowired private SpeciesRepository speciesRepository;

    // --------------------------------------------------------- misaada

    private String createMutation(String name, String growthMonths, String weightKg) {
        return "mutation { createSpecies(name: \"" + name + "\", growthMonthsAvg: " + growthMonths
                + ", avgHarvestWeightKg: " + weightKg
                + ") { speciesId name growthMonthsAvg avgHarvestWeightKg } }";
    }

    private JsonNode catalog() {
        return graphql(adminToken, SPECIES_QUERY).path("data").path("species");
    }

    private JsonNode rowNamed(String name) {
        for (JsonNode row : catalog()) {
            if (name.equals(row.path("name").asText())) {
                return row;
            }
        }
        return null;
    }

    // =====================================================================

    @Nested
    @DisplayName("kusajili")
    class Creating {

        @Test
        @DisplayName("aina mpya inaingia kwenye katalogi na inasomeka mara moja")
        void createsAndLists() {
            JsonNode res = graphql(adminToken, createMutation("Perege", "5", "0.4"));

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode created = res.path("data").path("createSpecies");
            assertThat(created.path("name").asText()).isEqualTo("Perege");
            assertThat(created.path("speciesId").asInt()).isPositive();

            // Ndiyo hoja nzima ya V20: aina ipo kwenye orodha inayojaza
            // dropdown ya kuunda mzunguko, bila mtu kugusa database.
            assertThat(rowNamed("Perege")).isNotNull();
        }

        @Test
        @DisplayName("nafasi tupu pembeni mwa jina zinaondolewa")
        void trimsName() {
            graphql(adminToken, createMutation("  Kuhe  ", "8", "1.2"));

            assertThat(rowNamed("Kuhe")).isNotNull();
        }

        @Test
        @DisplayName("aina za V1 zinabaki - kusajili hakuziondoi")
        void keepsSeededSpecies() {
            graphql(adminToken, createMutation("Ngege", "7", "0.5"));

            assertThat(rowNamed("Sato")).isNotNull();
            assertThat(rowNamed("Kambale")).isNotNull();
        }
    }

    @Nested
    @DisplayName("usahihi wa namba")
    class Precision {

        /**
         * JARIBIO LA MSINGI LA MODULE HII. growthMonthsAvg ndiyo pekee
         * inayokokotoa expectedHarvestDate; nusu-mwezi ikikatwa, kila
         * mzunguko wa aina hii unatabiriwa wiki mbili mapema, kimyakimya.
         */
        @Test
        @DisplayName("nusu-mwezi INAHIFADHIWA - 6.5 haiwi 6")
        void keepsHalfMonthPrecision() {
            JsonNode res = graphql(adminToken, createMutation("Nusu", "6.5", "0.75"));

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("createSpecies").path("growthMonthsAvg").asDouble())
                    .isEqualTo(6.5);

            // Na si kwenye jibu la mutation pekee - kwenye DATABASE, ambayo
            // ndiyo CycleService itakayoisoma.
            Species saved = speciesRepository.findAll().stream()
                    .filter(s -> "Nusu".equals(s.getName()))
                    .findFirst().orElseThrow();
            assertThat(saved.getGrowthMonthsAvg()).isEqualByComparingTo(new BigDecimal("6.5"));
            assertThat(saved.getAvgHarvestWeightKg()).isEqualByComparingTo(new BigDecimal("0.75"));
        }

        @Test
        @DisplayName("uzito unahifadhi desimali mbili (NUMERIC(6,2))")
        void keepsTwoDecimalsOnWeight() {
            graphql(adminToken, createMutation("Uzito", "6", "1.25"));

            assertThat(rowNamed("Uzito").path("avgHarvestWeightKg").asDouble()).isEqualTo(1.25);
        }

        /**
         * 0.04 ni CHANYA, lakini kwa NUMERIC(4,1) ni 0.0 - na
         * CycleService.requireGrowthMonths inakataa sifuri. Bila ukaguzi
         * BAADA ya kuweka scale, katalogi ingebeba aina isiyoweza KAMWE
         * kutumika, na kosa lingeonekana mbali na chanzo chake.
         */
        @Test
        @DisplayName("thamani inayoshuka hadi sifuri baada ya kuzungushwa inakataliwa")
        void refusesValueThatRoundsToZero() {
            JsonNode res = graphql(adminToken, createMutation("Ndogo", "0.04", "0.5"));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(rowNamed("Ndogo")).isNull();
        }

        @Test
        @DisplayName("thamani inayozidi ukubwa wa safu inakataliwa kwa ujumbe, si kwa overflow ya database")
        void refusesOverflow() {
            JsonNode res = graphql(adminToken, createMutation("Kubwa", "1000", "0.5"));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("999.9");
        }
    }

    @Nested
    @DisplayName("uthibitisho")
    class Validation {

        @Test
        @DisplayName("jina tupu linakataliwa")
        void refusesBlankName() {
            JsonNode res = graphql(adminToken, createMutation("   ", "6", "0.5"));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("muda wa kukua wa sifuri au hasi unakataliwa")
        void refusesNonPositiveGrowthMonths() {
            assertThat(graphqlErrorCode(graphql(adminToken, createMutation("Sifuri", "0", "0.5"))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(graphql(adminToken, createMutation("Hasi", "-3", "0.5"))))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("uzito wa sifuri au hasi unakataliwa")
        void refusesNonPositiveWeight() {
            assertThat(graphqlErrorCode(graphql(adminToken, createMutation("SifuriKg", "6", "0"))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(graphql(adminToken, createMutation("HasiKg", "6", "-1"))))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("jina lililochukuliwa linakataliwa kwa CONFLICT, si kwa hitilafu ya database")
        void refusesDuplicateName() {
            graphql(adminToken, createMutation("Rudufu", "6", "0.5"));

            JsonNode res = graphql(adminToken, createMutation("Rudufu", "8", "0.9"));

            assertThat(graphqlErrorCode(res)).isEqualTo("CONFLICT");
            // Sentensi yetu, si ile ya jumla ya vikwazo vya database.
            assertThat(graphqlMessage(res)).contains("jina hili tayari ipo");
        }

        @Test
        @DisplayName("jina la aina ya V1 linakataliwa vilevile")
        void refusesSeededName() {
            assertThat(graphqlErrorCode(graphql(adminToken, createMutation("Sato", "9", "0.6"))))
                    .isEqualTo("CONFLICT");
        }

        /**
         * `species.name` ni UNIQUE kwenye jedwali, lakini @SQLRestriction
         * inaificha aina iliyofutwa kwenye kila query ya JPA. Bila swali
         * la native, ukaguzi wetu ungepita na database ndiyo ingekataa -
         * CONFLICT yenye sentensi isiyomweleza msimamizi kwamba tatizo ni
         * jina ASILOLIONA.
         */
        @Test
        @DisplayName("jina la aina ILIYOFUTWA bado limechukuliwa")
        void refusesNameOfSoftDeletedSpecies() {
            graphql(adminToken, createMutation("Fichwa", "6", "0.5"));
            inTx(() -> {
                Species species = speciesRepository.findAll().stream()
                        .filter(s -> "Fichwa".equals(s.getName()))
                        .findFirst().orElseThrow();
                species.softDelete(adminId);
                return speciesRepository.save(species);
            });
            assertThat(rowNamed("Fichwa")).isNull();

            JsonNode res = graphql(adminToken, createMutation("Fichwa", "7", "0.6"));

            assertThat(graphqlErrorCode(res)).isEqualTo("CONFLICT");
            assertThat(graphqlMessage(res)).contains("jina hili tayari ipo");
        }
    }

    @Nested
    @DisplayName("ruhusa")
    class Permissions {

        @Test
        @DisplayName("WORKER hawezi kusajili - athari yake inavuka shamba lake")
        void workerCannotCreate() {
            JsonNode res = graphql(workerToken, createMutation("Wafanyakazi", "6", "0.5"));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(rowNamed("Wafanyakazi")).isNull();
        }

        @Test
        @DisplayName("VIEWER hawezi kusajili")
        void viewerCannotCreate() {
            JsonNode res = graphql(viewerToken, createMutation("Mtazamaji", "6", "0.5"));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(rowNamed("Mtazamaji")).isNull();
        }

        /**
         * Kusoma HAKUKUBADILIKA na V20. WORKER ndiye anayechagua aina
         * wakati wa kuunda mzunguko - akipoteza orodha, `manage_species`
         * ingekuwa imevunja kitu ambacho haikukusudia kukigusa.
         */
        @Test
        @DisplayName("WORKER na VIEWER wanaendelea KUSOMA katalogi")
        void readersKeepReading() {
            for (String token : new String[] {workerToken, viewerToken}) {
                JsonNode res = graphql(token, SPECIES_QUERY);
                assertThat(graphqlErrorCode(res)).isNull();
                assertThat(res.path("data").path("species")).isNotEmpty();
            }
        }

        @Test
        @DisplayName("asiye na role hana ruhusa - lango ni ruhusa, si uanachama")
        void noRoleCannotCreate() {
            JsonNode res = graphql(noroleToken, createMutation("Asiyekuwa", "6", "0.5"));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(rowNamed("Asiyekuwa")).isNull();
        }

        /**
         * Tabaka la KWANZA la SecurityConfig: ombi lisilo na token
         * linakatwa kabla halijafika injini ya GraphQL, hivyo jibu ni 401
         * ya ApiResponse - si `errors` ya GraphQL. Ndiyo maana jaribio hili
         * linaangalia status na `errorCode` ya juu, si graphqlErrorCode.
         */
        @Test
        @DisplayName("bila kikao ombi linakatwa kabla ya resolver - 401 UNAUTHENTICATED")
        void anonymousCannotCreate() throws Exception {
            String body = json.writeValueAsString(
                    Map.of("query", createMutation("Mgeni", "6", "0.5")));

            ResponseEntity<String> response = post("/graphql", body, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(parse(response).path("errorCode").asText()).isEqualTo("UNAUTHENTICATED");
            assertThat(rowNamed("Mgeni")).isNull();
        }
    }
}
