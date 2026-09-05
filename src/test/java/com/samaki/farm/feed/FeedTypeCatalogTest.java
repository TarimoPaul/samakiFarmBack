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
 * KUNDI C2 - kusimamia KATALOGI ya chakula (si ulishaji wenyewe).
 *
 * Katalogi ilikuwa na operesheni MOJA ya kuandika - kusajili. Skrini ya
 * usimamizi inahitaji nne, na tatu kati yao zinagusa data iliyopo, ndipo
 * hatari ilipo.
 *
 * KITU KIMOJA KINAELEZA MAJARIBIO MENGI HAPA: FeedType ina
 * `@SQLRestriction("is_deleted = false")`, na `FeedingLog.feedType` ni
 * `FeedType!` kwenye schema. Kuunganisha hivyo viwili kunamaanisha
 * soft-delete ya aina inayotumika HAIFICHI mstari mmoja - inavunja query
 * nzima. Jaribio la "ushahidi wa hatari" hapa chini linaonyesha hilo kwa
 * vitendo, na ndilo linaloeleza kwa nini deleteFeedType ina kikwazo badala
 * ya kufuta tu.
 *
 * KUZIMA na KUFUTA ni vitu viwili tofauti, na majaribio yanavitenganisha:
 * kuzima ni kwa aina iliyowahi kutumika (inabaki, historia inasomeka,
 * haichaguliwi tena), kufuta ni kwa aina isiyowahi kutumika (ilisajiliwa
 * kimakosa).
 */
@DisplayName("C2 - Katalogi ya chakula")
class FeedTypeCatalogTest extends IntegrationTest {

    private static final String CATALOG_QUERY =
            "query { feedTypes(activeOnly: false) { feedTypeId name minAgeMonths maxAgeMonths active } }";
    private static final String ACTIVE_CATALOG_QUERY =
            "query { feedTypes { feedTypeId name active } }";

    /**
     * Ushahidi wa hatari unahitaji kuandika `is_deleted` MOJA KWA MOJA,
     * bila kupitia service - ndiyo hasa hali ambayo kikwazo cha service
     * kinaizuia, hivyo haiwezi kufikiwa kupitia API.
     */
    @Autowired private FeedTypeRepository feedTypeRepository;

    // --------------------------------------------------------- misaada

    private int createFeedType(String name, int min, int max) {
        JsonNode res = graphql(adminToken, "mutation { createFeedType(name: \"" + name
                + "\", minAgeMonths: " + min + ", maxAgeMonths: " + max + ") { feedTypeId } }");
        return res.path("data").path("createFeedType").path("feedTypeId").asInt();
    }

    private String updateMutation(int id, String name, int min, int max) {
        return "mutation { updateFeedType(feedTypeId: " + id + ", name: \"" + name
                + "\", minAgeMonths: " + min + ", maxAgeMonths: " + max
                + ") { feedTypeId name minAgeMonths maxAgeMonths active } }";
    }

    private String setActiveMutation(int id, boolean active) {
        return "mutation { setFeedTypeActive(feedTypeId: " + id + ", active: " + active
                + ") { feedTypeId name active } }";
    }

    private String deleteMutation(int id) {
        return "mutation { deleteFeedType(feedTypeId: " + id + ") }";
    }

    private JsonNode catalog() {
        return graphql(adminToken, CATALOG_QUERY).path("data").path("feedTypes");
    }

    private JsonNode rowFor(int feedTypeId) {
        for (JsonNode row : catalog()) {
            if (row.path("feedTypeId").asInt() == feedTypeId) {
                return row;
            }
        }
        return null;
    }

    /** Aina inayotumika: ununuzi mmoja, hivyo leja na manunuzi vinaielekea. */
    private int usedFeedType() {
        int id = createFeedType("PELLET_USED", 0, 12);
        graphql(adminToken, "mutation { recordFeedPurchase(input: {purchaseDate: \"2026-09-01\", "
                + "feedTypeId: " + id + ", quantityKg: 50, unitCost: 1200, supplier: \"Duka\"}) "
                + "{ purchaseId } }");
        return id;
    }

    // =====================================================================

    @Nested
    @DisplayName("kuhariri")
    class Updating {

        @Test
        @DisplayName("jina na dirisha vinabadilika, na katalogi inaonyesha vipya")
        void updatesNameAndWindow() {
            int id = createFeedType("PELLET_A", 0, 6);

            JsonNode res = graphql(adminToken, updateMutation(id, "PELLET_B", 2, 9));

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode updated = res.path("data").path("updateFeedType");
            assertThat(updated.path("name").asText()).isEqualTo("PELLET_B");
            assertThat(updated.path("minAgeMonths").asInt()).isEqualTo(2);
            assertThat(updated.path("maxAgeMonths").asInt()).isEqualTo(9);
            // `active` haiguswi na kuhariri - ina mutation yake.
            assertThat(updated.path("active").asBoolean()).isTrue();

            assertThat(rowFor(id).path("name").asText()).isEqualTo("PELLET_B");
        }

        @Test
        @DisplayName("dirisha lililopinduka linakataliwa, kama wakati wa kusajili")
        void refusesInvertedWindow() {
            int id = createFeedType("PELLET_C", 0, 6);

            JsonNode res = graphql(adminToken, updateMutation(id, "PELLET_C", 9, 2));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            // Ujumbe unataja NAMBA ZOTE MBILI - ndio unaotumiwa na frontend
            // kama ulivyo, badala ya sentensi ya jumla.
            assertThat(res.path("errors").get(0).path("message").asText())
                    .contains("9").contains("2");
            // Hakuna kilichobadilika.
            assertThat(rowFor(id).path("maxAgeMonths").asInt()).isEqualTo(6);
        }

        @Test
        @DisplayName("umri hasi unakataliwa")
        void refusesNegativeAge() {
            int id = createFeedType("PELLET_D", 0, 6);

            JsonNode res = graphql(adminToken, updateMutation(id, "PELLET_D", -1, 6));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("jina la aina nyingine linakataliwa kwa CONFLICT, si kwa hitilafu ya database")
        void refusesNameTakenByAnother() {
            createFeedType("PELLET_E", 0, 6);
            int other = createFeedType("PELLET_F", 7, 12);

            JsonNode res = graphql(adminToken, updateMutation(other, "PELLET_E", 7, 12));

            assertThat(graphqlErrorCode(res)).isEqualTo("CONFLICT");
            // Sentensi yetu, si ile ya jumla ya vikwazo vya database.
            assertThat(res.path("errors").get(0).path("message").asText())
                    .contains("jina hili tayari ipo");
        }

        @Test
        @DisplayName("kuhifadhi aina bila kubadilisha jina lake si rudufu")
        void ownNameIsNotADuplicate() {
            int id = createFeedType("PELLET_G", 0, 6);

            JsonNode res = graphql(adminToken, updateMutation(id, "PELLET_G", 1, 8));

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(rowFor(id).path("minAgeMonths").asInt()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("kuzima na kurudisha")
    class Disabling {

        @Test
        @DisplayName("iliyozimwa inabaki kwenye katalogi lakini haichaguliwi tena")
        void disabledStaysButIsNotOffered() {
            int id = usedFeedType();

            assertThat(graphqlErrorCode(graphql(adminToken, setActiveMutation(id, false)))).isNull();

            // Bado ipo, ikiwa imezimwa - hii ndiyo tofauti na kufuta.
            assertThat(rowFor(id).path("active").asBoolean()).isFalse();

            // Haipo tena kwenye orodha ya zinazotumika, ambayo ndiyo
            // inayojaza dropdown ya ulishaji.
            JsonNode active = graphql(adminToken, ACTIVE_CATALOG_QUERY).path("data").path("feedTypes");
            for (JsonNode row : active) {
                assertThat(row.path("feedTypeId").asInt()).isNotEqualTo(id);
            }
        }

        @Test
        @DisplayName("kuzima hakuvunji historia ya ulishaji")
        void disablingKeepsHistoryReadable() {
            int id = usedFeedType();
            graphql(workerToken, "mutation { logFeeding(input: {cycleId: " + cycleA
                    + ", quantityKg: 5, feedTypeId: " + id + "}) { logId } }");

            graphql(adminToken, setActiveMutation(id, false));

            JsonNode logs = graphql(adminToken,
                    "query { feedingLogs(cycleId: " + cycleA + ") { logId feedType { feedTypeId name } } }");
            assertThat(graphqlErrorCode(logs)).isNull();
            assertThat(logs.path("data").path("feedingLogs")).isNotEmpty();
        }

        @Test
        @DisplayName("kurudisha kunairejesha kwenye orodha ya kuchagua")
        void enablingRestoresIt() {
            int id = createFeedType("PELLET_H", 0, 12);
            graphql(adminToken, setActiveMutation(id, false));

            graphql(adminToken, setActiveMutation(id, true));

            assertThat(rowFor(id).path("active").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("kuzima iliyokwisha zimwa ni sawa")
        void isIdempotent() {
            int id = createFeedType("PELLET_I", 0, 12);
            graphql(adminToken, setActiveMutation(id, false));

            JsonNode res = graphql(adminToken, setActiveMutation(id, false));

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("setFeedTypeActive").path("active").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("kufuta")
    class Deleting {

        @Test
        @DisplayName("aina isiyowahi kutumika inafutika, na inatoweka kwenye katalogi")
        void deletesAnUnusedType() {
            int id = createFeedType("TYPO_TYPE", 0, 3);

            JsonNode res = graphql(adminToken, deleteMutation(id));

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("deleteFeedType").asBoolean()).isTrue();
            // activeOnly: false inaomba KILA kitu, na bado haipo - ndiyo
            // tofauti kati ya kufuta na kuzima.
            assertThat(rowFor(id)).isNull();
        }

        @Test
        @DisplayName("aina inayotumika INAKATALIWA, na ujumbe unapendekeza kuizima")
        void refusesATypeInUse() {
            int id = usedFeedType();

            JsonNode res = graphql(adminToken, deleteMutation(id));

            assertThat(graphqlErrorCode(res)).isEqualTo("FEED_TYPE_IN_USE");
            String message = res.path("errors").get(0).path("message").asText();
            assertThat(message).contains("izime");
            // Bado ipo kabisa.
            assertThat(rowFor(id)).isNotNull();
        }

        /**
         * USHAHIDI WA HATARI - kwa nini kikwazo hapo juu kipo.
         *
         * Hapa `is_deleted` inawekwa MOJA KWA MOJA kwenye repository,
         * ikipita kando ya service, ili kuonyesha kinachotokea kama
         * soft-delete ingeruhusiwa kwa aina inayotumika: si mstari mmoja
         * unaopotea kwenye historia - ni `feedingLogs` NZIMA inayoacha
         * kusomeka, kwa sababu `FeedingLog.feedType` ni `FeedType!` na
         * @SQLRestriction inaificha aina.
         *
         * Jaribio hili likianza kufeli, maana yake ni kwamba tabia ya
         * Hibernate imebadilika na kikwazo cha deleteFeedType kinaweza
         * kupitiwa upya - si kwamba kitu kimeharibika.
         */
        @Test
        @DisplayName("ushahidi: soft-delete ya aina inayotumika ingevunja historia yote")
        void softDeletingAUsedTypeWouldBreakHistory() {
            int id = usedFeedType();
            graphql(workerToken, "mutation { logFeeding(input: {cycleId: " + cycleA
                    + ", quantityKg: 5, feedTypeId: " + id + "}) { logId } }");

            String logsQuery =
                    "query { feedingLogs(cycleId: " + cycleA + ") { logId feedType { feedTypeId name } } }";
            assertThat(graphqlErrorCode(graphql(adminToken, logsQuery))).isNull();

            // Kupita kando ya kikwazo, kwa makusudi.
            inTx(() -> {
                FeedType type = feedTypeRepository.findById(id).orElseThrow();
                type.softDelete(adminId);
                return feedTypeRepository.save(type);
            });

            JsonNode broken = graphql(adminToken, logsQuery);
            assertThat(broken.path("errors").isArray() && !broken.path("errors").isEmpty())
                    .as("historia ingeacha kusomeka - ndiyo sababu ya FEED_TYPE_IN_USE")
                    .isTrue();
        }

        @Test
        @DisplayName("jina la aina iliyofutwa bado limechukuliwa, na linaelezwa hivyo")
        void deletedNameIsStillTaken() {
            int id = createFeedType("GHOST", 0, 3);
            graphql(adminToken, deleteMutation(id));

            JsonNode res = graphql(adminToken,
                    "mutation { createFeedType(name: \"GHOST\", minAgeMonths: 0, maxAgeMonths: 3) { feedTypeId } }");

            // `feed_types.name` ni UNIQUE na safu iliyofutwa bado ipo, hivyo
            // database ingekataa. Inakamatwa na ukaguzi wetu badala yake,
            // hivyo ujumbe ni kuhusu JINA - si kuhusu vikwazo vya database.
            assertThat(graphqlErrorCode(res)).isEqualTo("CONFLICT");
            assertThat(res.path("errors").get(0).path("message").asText())
                    .contains("jina hili tayari ipo");
        }
    }

    @Nested
    @DisplayName("ruhusa")
    class Permissions {

        @Test
        @DisplayName("mwenye log_feeding pekee hawezi kuhariri, kuzima wala kufuta")
        void feederCannotManageTheCatalogue() {
            int id = createFeedType("PELLET_J", 0, 12);

            assertThat(graphqlErrorCode(graphql(workerToken, updateMutation(id, "X", 0, 12))))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(workerToken, setActiveMutation(id, false))))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(workerToken, deleteMutation(id))))
                    .isEqualTo("FORBIDDEN");

            // Hakuna hata moja iliyopita.
            assertThat(rowFor(id).path("active").asBoolean()).isTrue();
            assertThat(rowFor(id).path("name").asText()).isEqualTo("PELLET_J");
        }

        @Test
        @DisplayName("hata kusoma katalogi ni manage_feed_stock, si view_dashboard")
        void readingTheCatalogueIsAlsoGated() {
            assertThat(graphqlErrorCode(graphql(workerToken, CATALOG_QUERY))).isEqualTo("FORBIDDEN");
        }
    }
}
