package com.samaki.farm.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI C3 - BEI YA CHAKULA (`view_feed_cost`, V18).
 *
 * Mstari mmoja wa manunuzi unabeba vitu viwili visivyofanana:
 *
 *   * kiasi, aina, tarehe, muuzaji - taarifa ya UENDESHAJI. Anayeshika
 *     chakula anahitaji kujua magunia mangapi yaliingia na lini.
 *   * unitCost / totalCost         - taarifa ya BEI, yaani mkataba wa
 *     kibiashara kati ya shamba na muuzaji.
 *
 * Hadi V18 vyote vilisafiri pamoja kwa mwenye `view_dashboard` yeyote -
 * ikiwemo WORKER na VIEWER.
 *
 * KITU KIMOJA KINAELEZA MAJARIBIO YOTE HAPA: ufichaji uko SERVER, si
 * kwenye UI. Jedwali lisilo na safu ya bei bado lingekuwa limepokea bei
 * ndani ya JSON, ikionekana kwa yeyote anayefungua DevTools au
 * anayepiga /graphql kwa token yake ile ile. Ndiyo maana kila jaribio
 * hapa linaangalia THAMANI ILIYOFIKA kwenye jibu, si kinachoonyeshwa.
 *
 * Ruhusa mbili zinabaki TOFAUTI kwa makusudi, na majaribio yanavitenganisha
 * pande zote mbili: `manage_feed_stock` ni kununua, `view_feed_cost` ni
 * kuona bei ya ununuzi. Mwenye ya kwanza bila ya pili ndiye mhusika mkuu
 * wa kundi hili.
 */
@DisplayName("C3 - Bei ya chakula (view_feed_cost)")
class FeedCostVisibilityTest extends IntegrationTest {

    private static final String PURCHASES_QUERY =
            "query { feedPurchases { purchaseId purchaseDate quantityKg unitCost totalCost "
                    + "supplier feedType { feedTypeId name } } }";

    /** Chakula cha majaribio: [0, 12] miezi, kama FeedRegressionTest. */
    private Integer pelletId;

    private int pellet() {
        if (pelletId == null) {
            pelletId = graphql(adminToken,
                    "mutation { createFeedType(name: \"PELLET\", minAgeMonths: 0, maxAgeMonths: 12)"
                            + " { feedTypeId } }")
                    .path("data").path("createFeedType").path("feedTypeId").asInt();
        }
        return pelletId;
    }

    // --------------------------------------------------------- misaada

    /** kg 80 kwa 1500/kg = 120,000 - namba zinazotofautiana wazi kwenye assert. */
    private String purchaseMutation(double kg, double unitCost) {
        return "mutation { recordFeedPurchase(input: {purchaseDate: \"2026-09-01\", feedTypeId: "
                + pellet() + ", quantityKg: " + kg + ", unitCost: " + unitCost
                + ", supplier: \"Duka\"}) { purchaseId quantityKg unitCost totalCost } }";
    }

    /** Mstari wa kwanza wa feedPurchases kama MWOMBAJI HUYU anavyouona. */
    private JsonNode firstPurchase(String token) {
        JsonNode response = graphql(token, PURCHASES_QUERY);
        assertThat(graphqlErrorCode(response)).isNull();
        JsonNode rows = response.path("data").path("feedPurchases");
        assertThat(rows).isNotEmpty();
        return rows.get(0);
    }

    /**
     * Inaweka ruhusa za role UPYA KABISA (replace, si kuongeza) - ndiyo
     * semantiki ya PUT /api/roles/{id}/permissions.
     *
     * Ni njia ile ile AuthRegressionTest (D-13) inayotumia kuonyesha kwamba
     * RBAC inahaririwa wakati wa run. Inatumika hapa kwa sababu fixture ya
     * dev haina mhusika mwenye `manage_feed_stock` BILA `view_feed_cost` -
     * OWNER ana zote mbili, WORKER hana yoyote - na huyo ndiye mtu ambaye
     * ruhusa hizi mbili zimetenganishwa kwa ajili yake.
     */
    private void setPermissions(String roleName, String... codes) {
        ResponseEntity<String> response = put("/api/roles/" + roleId(roleName) + "/permissions",
                permissionIdsFor(codes), adminToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private int roleId(String name) {
        for (JsonNode role : parse(get("/api/roles", adminToken)).path("data")) {
            if (name.equals(role.path("name").asText())) {
                return role.path("roleId").asInt();
            }
        }
        throw new IllegalStateException("Role haipo: " + name);
    }

    private String permissionIdsFor(String... codes) {
        JsonNode page = parse(get("/api/roles/permissions?page=0&size=200", adminToken)).path("data");
        JsonNode items = page.isArray() ? page : page.path("content");
        List<String> ids = new ArrayList<>();
        for (JsonNode permission : items) {
            for (String code : codes) {
                if (code.equals(permission.path("code").asText())) {
                    ids.add(String.valueOf(permission.path("permissionId").asInt()));
                }
            }
        }
        // Ruhusa iliyoombwa isiyopatikana kwenye katalogi ingefanya jaribio
        // lipite likiwa limejaribu kitu kingine kabisa - mfano `view_feed_cost`
        // ikiwa haijaingizwa kwenye permissions.csv. Inagundulika hapa.
        assertThat(ids).hasSameSizeAs(codes);
        return "[" + String.join(",", ids) + "]";
    }

    @Nested
    @DisplayName("nani anaona bei")
    class WhoSeesCost {

        @Test
        @DisplayName("mwenye view_feed_cost anaona bei halisi")
        void holderSeesCosts() {
            // OWNER anaipata kutoka seed - angalia role_permissions.csv na V18.
            graphql(adminToken, purchaseMutation(80, 1500));

            JsonNode row = firstPurchase(adminToken);

            assertThat(row.path("unitCost").asDouble()).isEqualTo(1500.0);
            assertThat(row.path("totalCost").asDouble()).isEqualTo(120000.0);
        }

        @Test
        @DisplayName("mwenye manage_feed_stock BILA view_feed_cost: bei ni null, kiasi ni halisi")
        void stockManagerWithoutCostPermissionSeesNulls() {
            graphql(adminToken, purchaseMutation(80, 1500));

            // Mnunuzi kamili wa chakula: anaruhusiwa kununua na kusimamia
            // stoo, hana ruhusa ya bei. Hii ndiyo hali hasa ambayo ruhusa
            // mbili tofauti zipo kwa ajili yake.
            setPermissions("WORKER", "view_dashboard", "log_feeding",
                    "view_feed_stock", "manage_feed_stock");
            // Thibitisho kwamba manage_feed_stock anayo kweli - vinginevyo
            // jaribio lingekuwa linapima mtu asiye na kitu.
            assertThat(graphqlErrorCode(graphql(workerToken, "query { feedTypes { feedTypeId } }")))
                    .isNull();

            JsonNode row = firstPurchase(workerToken);

            // Bei HAIKUFIKA kabisa. `isNull` - si sifuri, si kukosekana kwa uga.
            assertThat(row.path("unitCost").isNull()).isTrue();
            assertThat(row.path("totalCost").isNull()).isTrue();

            // Kila kingine kama kilivyo: mstari haujafichwa, ni safu MBILI
            // pekee zilizoondolewa ndani yake.
            assertThat(row.path("quantityKg").asDouble()).isEqualTo(80.0);
            assertThat(row.path("purchaseDate").asText()).isEqualTo("2026-09-01");
            assertThat(row.path("supplier").asText()).isEqualTo("Duka");
            assertThat(row.path("feedType").path("name").asText()).isEqualTo("PELLET");
            assertThat(row.path("purchaseId").asText()).isNotBlank();
        }

        @Test
        @DisplayName("VIEWER anaona manunuzi bila bei")
        void viewerSeesRowsWithoutCosts() {
            graphql(adminToken, purchaseMutation(80, 1500));

            // V17 iliacha feedPurchases kwenye view_dashboard kwa makusudi.
            // V18 HAIZUII mstari - inaondoa namba mbili ndani yake.
            JsonNode row = firstPurchase(viewerToken);

            assertThat(row.path("quantityKg").asDouble()).isEqualTo(80.0);
            assertThat(row.path("unitCost").isNull()).isTrue();
            assertThat(row.path("totalCost").isNull()).isTrue();
        }

        @Test
        @DisplayName("kupewa view_feed_cost kunarudisha bei - lango linafanya kazi pande zote mbili")
        void grantingThePermissionRevealsCosts() {
            graphql(adminToken, purchaseMutation(80, 1500));

            setPermissions("WORKER", "view_dashboard", "view_feed_stock");
            assertThat(firstPurchase(workerToken).path("unitCost").isNull()).isTrue();

            setPermissions("WORKER", "view_dashboard", "view_feed_stock", "view_feed_cost");

            // Token ILE ILE, ruhusa mpya (D-13): ufichaji unategemea ruhusa
            // ya sasa, si iliyokuwepo wakati wa login. Jaribio lingelipita
            // hata kama ufichaji ungekuwa umeandikwa kwa jina la role -
            // ndiyo maana lipo pia upande wa kufungua, si wa kufunga pekee.
            JsonNode row = firstPurchase(workerToken);
            assertThat(row.path("unitCost").asDouble()).isEqualTo(1500.0);
            assertThat(row.path("totalCost").asDouble()).isEqualTo(120000.0);
        }

        @Test
        @DisplayName("kusoma kulikofichwa HAKUFUTI bei kwenye database")
        void maskingIsReadOnly() {
            graphql(adminToken, purchaseMutation(80, 1500));

            setPermissions("WORKER", "view_dashboard", "manage_feed_stock", "view_feed_stock");
            assertThat(firstPurchase(workerToken).path("unitCost").isNull()).isTrue();

            // MTEGO ULIOEPUKWA. Njia fupi ya kuficha ingekuwa
            // `purchase.setUnitCost(null)` juu ya entity iliyo hai ndani ya
            // transaction ya listPurchases; Hibernate ingeona mabadiliko na
            // kujaribu kuandika NULL kwenye `unit_cost` (NOT NULL) - yaani
            // KUSOMA kwa asiyeruhusiwa kungeharibu rekodi ya fedha.
            // FeedPurchaseView ni record ya kusomwa tu, hivyo bei inabaki.
            JsonNode row = firstPurchase(adminToken);
            assertThat(row.path("unitCost").asDouble()).isEqualTo(1500.0);
            assertThat(row.path("totalCost").asDouble()).isEqualTo(120000.0);
        }
    }

    @Nested
    @DisplayName("kununua hakukuguswa")
    class RecordingIsUnchanged {

        @Test
        @DisplayName("recordFeedPurchase bado inahitaji manage_feed_stock")
        void recordingStillNeedsManageFeedStock() {
            // WORKER wa seed ana log_feeding/view_feed_stock, si
            // manage_feed_stock. V18 haikugusa lango hili hata kidogo.
            JsonNode response = graphql(workerToken, purchaseMutation(10, 900));
            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("manage_feed_stock");

            assertThat(graphqlErrorCode(graphql(viewerToken, purchaseMutation(10, 900))))
                    .isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("view_feed_cost HAIMFUNGULII mtu kununua")
        void seeingCostIsNotBuying() {
            // Ruhusa ya kusoma bei si ruhusa ya kuandika manunuzi. Kama
            // ufichaji ungewekwa kwa kuchanganya ruhusa hizi mbili, jaribio
            // hili lingeanguka.
            setPermissions("WORKER", "view_dashboard", "view_feed_stock", "view_feed_cost");

            JsonNode response = graphql(workerToken, purchaseMutation(10, 900));
            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("manage_feed_stock");
        }

        @Test
        @DisplayName("jibu la recordFeedPurchase halifichwi - mnunuzi ndiye aliyeandika bei")
        void theBuyersOwnReceiptIsNotMasked() {
            setPermissions("WORKER", "view_dashboard", "manage_feed_stock", "view_feed_stock");

            JsonNode created = graphql(workerToken, purchaseMutation(80, 1500))
                    .path("data").path("recordFeedPurchase");

            // unitCost ni namba ALIYOITUMA yeye mwenyewe kwenye input hii, na
            // totalCost ni kuzidisha kwake. Kuificha kungemzuia mnunuzi
            // kuthibitisha alichokiandika bila kumficha chochote asichokijua.
            assertThat(created.path("unitCost").asDouble()).isEqualTo(1500.0);
            assertThat(created.path("totalCost").asDouble()).isEqualTo(120000.0);

            // Lakini ORODHA - inayoonyesha manunuzi ya WATU WOTE wa shamba -
            // inabaki imefichwa kwake.
            assertThat(firstPurchase(workerToken).path("unitCost").isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("totalCost inatoka database")
    class GeneratedTotal {

        @Test
        @DisplayName("kuituma kwenye input ni kosa la schema - haifiki service")
        void totalCostIsNotAnInputField() {
            JsonNode rejected = graphql(adminToken,
                    "mutation { recordFeedPurchase(input: {purchaseDate: \"2026-09-01\", feedTypeId: "
                            + pellet() + ", quantityKg: 80, unitCost: 1500, totalCost: 1, "
                            + "supplier: \"Duka\"}) { purchaseId totalCost } }");

            // RecordFeedPurchaseInput haina uga huo kabisa, hivyo ombi
            // linakataliwa wakati wa kuthibitisha query - kabla ya resolver
            // yoyote kuitwa.
            assertThat(rejected.path("errors")).isNotEmpty();
            assertThat(graphqlMessage(rejected)).contains("totalCost");
            assertThat(rejected.path("data").path("recordFeedPurchase").isMissingNode()
                    || rejected.path("data").path("recordFeedPurchase").isNull()).isTrue();
        }

        @Test
        @DisplayName("bila kuitumwa, inakuja imekokotolewa na database na inabaki hivyo")
        void totalCostComesBackComputed() {
            // GENERATED ALWAYS AS (quantity_kg * unit_cost) STORED (V1), na
            // FeedPurchase.totalCost ni insertable/updatable=false - hakuna
            // njia ya mteja WALA ya service kuipandikiza thamani nyingine.
            JsonNode created = graphql(adminToken, purchaseMutation(80, 1500))
                    .path("data").path("recordFeedPurchase");
            assertThat(created.path("totalCost").asDouble()).isEqualTo(120000.0);

            // Na ni ILE ILE inaposomwa tena: imehifadhiwa, haikokotolewi
            // upya na Java popote.
            assertThat(firstPurchase(adminToken).path("totalCost").asDouble()).isEqualTo(120000.0);
        }
    }
}
