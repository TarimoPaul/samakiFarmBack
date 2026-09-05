package com.samaki.farm.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Maisha ya shamba: kuunda, kuhariri, kufuta.
 *
 * PUT na DELETE hazikuwepo kabisa - FarmService yenyewe ilisema
 * "kubadilisha jina/mahali hakujajengwa bado". Mambo MATATU yanajaribiwa
 * hapa, na kila moja ni sheria iliyoamuliwa kwa makusudi:
 *
 *  1. KUFUTA kunahitaji ruhusa YAKE MWENYEWE (`delete_farm`), si
 *     `manage_farms` inayotosha kuunda na kuhariri. Shamba ndilo muktadha wa
 *     kila kitu kilichomo, hivyo kulifuta si sawa na kulipa jina jipya.
 *  2. Shamba lenye WANACHAMA halifutwi - safu zao za `farm_users`
 *     zingebaki zikielekeza mahali pasipoonekana.
 *  3. Jina la shamba LILILOFUTWA linaachiwa huru - ndilo lengo la V14,
 *     ambalo V9 iliahidi kwa maneno yake yenyewe.
 */
@DisplayName("Shamba - kuunda, kuhariri, kufuta")
class FarmCrudRegressionTest extends IntegrationTest {

    // ------------------------------------------------------------ kuhariri

    @Test
    @DisplayName("jina na eneo vinabadilika")
    void nameAndLocationAreUpdated() {
        ResponseEntity<String> response =
                put("/api/farms/" + farmA, "{\"name\":\"Shamba Jipya\",\"location\":\"Njombe\"}",
                        adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode farm = farmById(farmA);
        assertThat(farm.path("name").asText()).isEqualTo("Shamba Jipya");
        assertThat(farm.path("location").asText()).isEqualTo("Njombe");
    }

    @Test
    @DisplayName("jina tupu: 400 VALIDATION_ERROR")
    void blankNameIsRefused() {
        ResponseEntity<String> response =
                put("/api/farms/" + farmA, "{\"name\":\"   \",\"location\":\"x\"}", adminToken);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("jina la shamba lingine: 409 ikitaja jina, si vikwazo vya database")
    void aTakenNameIsNamed() {
        String otherName = farmById(farmB).path("name").asText();

        ResponseEntity<String> response = put("/api/farms/" + farmA,
                "{\"name\":\"" + otherName + "\",\"location\":null}", adminToken);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.path("message").asText()).isEqualTo("Shamba lenye jina hili tayari lipo.");
        assertThat(body.path("message").asText()).doesNotContain("vikwazo vya database");
    }

    @Test
    @DisplayName("kubakiza jina lake mwenyewe si rudufu")
    void keepingItsOwnNameIsNotADuplicate() {
        String ownName = farmById(farmA).path("name").asText();

        ResponseEntity<String> response = put("/api/farms/" + farmA,
                "{\"name\":\"" + ownName + "\",\"location\":\"Eneo Jipya\"}", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(farmById(farmA).path("location").asText()).isEqualTo("Eneo Jipya");
    }

    // --------------------------------------------------------------- kufuta

    @Test
    @DisplayName("shamba lenye wanachama: 409 + FARM_IN_USE ikitaja idadi, na haliguswi")
    void aFarmWithMembersIsRefused() {
        ResponseEntity<String> response = delete("/api/farms/" + farmA, adminToken);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.path("errorCode").asText()).isEqualTo("FARM_IN_USE");
        assertThat(body.path("message").asText()).contains("lina wanachama");

        // Na muhimu zaidi: bado lipo, na wanachama wake hawajaguswa.
        assertThat(farmById(farmA).isMissingNode()).isFalse();
        assertThat(parse(get("/api/users?farmId=" + farmA, adminToken)).path("data")).isNotEmpty();
    }

    @Test
    @DisplayName("shamba lisilo na mtu linafutwa, na linatoweka kwenye orodha")
    void anEmptyFarmIsDeleted() {
        int spare = createFarm("Shamba la Muda");

        ResponseEntity<String> response = delete("/api/farms/" + spare, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(farmById(spare).isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("kufuta mara ya pili ni 400 - findById peke yake ingesema imefanikiwa")
    void deletingTwiceIsRefused() {
        int spare = createFarm("Shamba la Muda");
        delete("/api/farms/" + spare, adminToken);

        ResponseEntity<String> second = delete("/api/farms/" + spare, adminToken);

        // Hibernate haitumii @SQLRestriction kwenye lookup ya PK, hivyo bila
        // findByFarmId (derived) ndani ya requireFarm hii ingekuwa 200.
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parse(second).path("message").asText()).isEqualTo("Shamba halipo");
    }

    @Test
    @DisplayName("jina la shamba lililofutwa linaachiwa huru - ndilo lengo la V14")
    void aDeletedFarmReleasesItsName() {
        int spare = createFarm("Shamba la Mbeya");
        delete("/api/farms/" + spare, adminToken);

        // V9 iliweka UNIQUE ya kawaida na kuandika kwamba igeuzwe kuwa partial
        // index siku endpoint ya kufuta ikija. Bila V14, hii ingekuwa 409
        // ikizuiwa na safu isiyoonekana popote kwenye mfumo.
        ResponseEntity<String> again = post("/api/farms",
                "{\"name\":\"Shamba la Mbeya\",\"location\":null}", adminToken);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --------------------------------------------------------------- ruhusa

    @Test
    @DisplayName("asiye na manage_farms hawezi kuhariri wala kufuta")
    void withoutManageFarmsNothingIsAllowed() {
        assertThat(put("/api/farms/" + farmA, "{\"name\":\"X\"}", workerToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete("/api/farms/" + farmA, workerToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("delete_farm ni ruhusa YAKE: ikiondolewa, kuhariri kunabaki lakini kufuta kunakoma")
    void deleteNeedsItsOwnPermission() {
        int spare = createFarm("Shamba la Muda");

        // Ondoa `delete_farm` kwa OWNER, ukiacha kila ruhusa nyingine kama
        // ilivyo. Huu ndio mgawanyo wenyewe unaojaribiwa: bila kuwa ruhusa
        // ya pekee, hakungekuwa na cha kuondoa.
        stripPermissionFromOwner("delete_farm");

        assertThat(delete("/api/farms/" + spare, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // manage_farms haijaguswa, hivyo kuhariri bado kunafanya kazi.
        assertThat(put("/api/farms/" + spare, "{\"name\":\"Bado Naweza\"}", adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------- msaidizi

    private int createFarm(String name) {
        ResponseEntity<String> response =
                post("/api/farms", "{\"name\":\"" + name + "\",\"location\":null}", adminToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(response).path("data").path("farmId").asInt();
    }

    private JsonNode farmById(int farmId) {
        for (JsonNode farm : parse(get("/api/farms", adminToken)).path("data")) {
            if (farm.path("farmId").asInt() == farmId) {
                return farm;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    /** Andika upya ruhusa za OWNER bila msimbo mmoja - kila nyingine ibaki. */
    private void stripPermissionFromOwner(String code) {
        JsonNode catalogue = parse(get("/api/roles/permissions?page=0&size=200", adminToken))
                .path("data");

        int ownerRoleId = -1;
        List<String> ownerCodes = new ArrayList<>();
        for (JsonNode role : parse(get("/api/roles", adminToken)).path("data")) {
            if ("OWNER".equals(role.path("name").asText())) {
                ownerRoleId = role.path("roleId").asInt();
                role.path("permissions").forEach(p -> ownerCodes.add(p.asText()));
            }
        }
        assertThat(ownerRoleId).isPositive();
        assertThat(ownerCodes).contains(code);

        List<String> keptIds = new ArrayList<>();
        for (JsonNode permission : catalogue) {
            String permissionCode = permission.path("code").asText();
            if (ownerCodes.contains(permissionCode) && !code.equals(permissionCode)) {
                keptIds.add(String.valueOf(permission.path("permissionId").asInt()));
            }
        }

        ResponseEntity<String> written = put("/api/roles/" + ownerRoleId + "/permissions",
                "[" + String.join(",", keptIds) + "]", adminToken);
        assertThat(written.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
