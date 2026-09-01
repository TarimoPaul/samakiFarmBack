package com.samaki.farm.farmuser;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI B - misimbo ya kigongano kwenye uanachama.
 *
 * Conflict ilikuwa hitilafu PEKEE iliyorudi bila errorCode, ikilazimisha
 * frontend kutawi kwa status 409 - njia tofauti na hitilafu nyingine
 * zote. Test hizi zinashikilia pande MBILI za marekebisho: kwamba msimbo
 * sasa upo, na kwamba ujumbe na status HAVIKUBADILIKA (ndicho kilichofanya
 * marekebisho yasivunje frontend iliyopo).
 */
@DisplayName("B - kigongano cha uanachama")
class MembershipConflictRegressionTest extends IntegrationTest {

    @Test
    @DisplayName("mmiliki hawezi kutolewa: 409 + OWNER_IMMUTABLE, ujumbe ule ule")
    void ownerRemovalIsRefusedWithItsOwnCode() {
        ResponseEntity<String> response =
                delete("/api/users/" + adminId + "/memberships/" + farmA, adminToken);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.path("errorCode").asText()).isEqualTo("OWNER_IMMUTABLE");
        // Ujumbe ni sehemu ya mkataba hapa: frontend inauonyesha kama
        // ulivyo, na kuubadilisha kungevunja skrini ya Members.
        assertThat(body.path("message").asText())
                .isEqualTo("Mmiliki wa shamba hawezi kutolewa kwenye shamba lake.");
        assertThat(body.path("success").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("uanachama unaojirudia: 409 + CONFLICT ya jumla")
    void duplicateMembershipGetsTheGenericCode() {
        ResponseEntity<String> response = post("/api/users/" + workerId + "/memberships",
                "{\"farmId\":" + farmA + ",\"roleId\":null}", adminToken);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Chaguo-msingi cha ConflictException. Kabla ya marekebisho hii
        // ilikuwa null kabisa.
        assertThat(body.path("errorCode").asText()).isEqualTo("CONFLICT");
        assertThat(body.path("message").asText())
                .isEqualTo("Mtumiaji huyu tayari yupo kwenye shamba hili.");
    }

    @Test
    @DisplayName("misimbo miwili inatofautiana - ndiyo sababu ya kuwa na wa pekee")
    void theTwoConflictsAreDistinguishable() {
        String owner = parse(delete("/api/users/" + adminId + "/memberships/" + farmA, adminToken))
                .path("errorCode").asText();
        String duplicate = parse(post("/api/users/" + workerId + "/memberships",
                "{\"farmId\":" + farmA + ",\"roleId\":null}", adminToken))
                .path("errorCode").asText();

        // Rudufu inaweza kurekebishwa na kujaribiwa tena; mmiliki hawezi
        // kutolewa kamwe. Skrini inapaswa kuzitofautisha bila kusoma
        // sentensi ya Kiswahili.
        assertThat(owner).isNotEqualTo(duplicate);
    }

    @Test
    @DisplayName("mwanachama wa kawaida ANATOLEWA - kikwazo ni cha mmiliki pekee")
    void anOrdinaryMemberCanBeRemoved() {
        ResponseEntity<String> response =
                delete("/api/users/" + workerId + "/memberships/" + farmA, workerRemovalToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(get("/api/users?farmId=" + farmA, adminToken)).path("data"))
                .noneSatisfy(node -> assertThat(node.path("phone").asText()).isEqualTo(WORKER_PHONE));
    }

    @Test
    @DisplayName("kubadilisha role kunafanikiwa, na /me ya mtu inabadilika")
    void changingRoleTakesEffect() {
        int viewerRoleId = roleIdByName("VIEWER");

        ResponseEntity<String> response = put(
                "/api/users/" + workerId + "/memberships/" + farmA + "/role",
                "{\"farmId\":" + farmA + ",\"roleId\":" + viewerRoleId + "}", adminToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Aliyekuwa WORKER sasa hawezi kurekodi kipimo - VIEWER hana
        // log_water_quality.
        assertThat(graphqlErrorCode(graphql(login(WORKER_PHONE),
                "mutation { logWaterQuality(input: {unitId: " + unitA + ", ph: 7.0}) { logId } }")))
                .isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("bila manage_users, kutoa mwanachama ni FORBIDDEN")
    void removalNeedsThePermission() {
        ResponseEntity<String> response =
                delete("/api/users/" + workerId + "/memberships/" + farmA, workerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(parse(response).path("errorCode").asText()).isEqualTo("FORBIDDEN");
    }

    /**
     * NGAZI MBILI za PermissionChecker.requireSameFarm, pande zote mbili.
     *
     * Dev Admin ana role ya OWNER, ambayo INAJUMUISHA manage_farms - hivyo
     * ni msimamizi wa KAMPUNI, na kufikia shamba jingine ni sahihi kwake.
     * Test ya kwanza ilidai kinyume na ikaanguka ikiwa 200; jibu la 200
     * lilikuwa sahihi, dai ndilo lililokuwa na kasoro.
     *
     * Ngazi ya chini inapimwa kwa kumvua manage_farms kupitia endpoint ile
     * ile ya D-13, kisha kurudia ombi lile lile.
     */
    @Test
    @DisplayName("mwenye manage_farms anafikia shamba lolote (ngazi ya kampuni)")
    void companyWideAdminReachesAnotherFarm() {
        UUID workerB = userRepository.findByPhone(WORKER_B_PHONE).orElseThrow().getUserId();

        ResponseEntity<String> response =
                delete("/api/users/" + workerB + "/memberships/" + farmB, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("mwenye manage_users pekee amefungiwa shamba lake (ngazi ya shamba)")
    void farmLevelAdminIsConfinedToTheirOwnFarm() {
        UUID workerB = userRepository.findByPhone(WORKER_B_PHONE).orElseThrow().getUserId();

        // Mvue OWNER manage_farms, ukimwachia manage_users: sasa ni
        // msimamizi wa shamba lake pekee.
        put("/api/roles/" + roleIdByName("OWNER") + "/permissions",
                permissionIdsFor("manage_users", "view_dashboard"), adminToken);
        String confined = login(ADMIN_PHONE);

        ResponseEntity<String> response =
                delete("/api/users/" + workerB + "/memberships/" + farmB, confined);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(parse(response).path("errorCode").asText()).isEqualTo("FORBIDDEN");

        // Lakini shamba LAKE bado liko wazi kwake.
        assertThat(delete("/api/users/" + workerId + "/memberships/" + farmA, confined)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String workerRemovalToken() {
        return adminToken;
    }

    private String permissionIdsFor(String... codes) {
        JsonNode page = parse(get("/api/roles/permissions?page=0&size=200", adminToken)).path("data");
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (JsonNode permission : page) {
            for (String code : codes) {
                if (code.equals(permission.path("code").asText())) {
                    ids.add(String.valueOf(permission.path("permissionId").asInt()));
                }
            }
        }
        if (ids.size() != codes.length) {
            throw new IllegalStateException("Ruhusa hazikupatikana zote: " + page);
        }
        return "[" + String.join(",", ids) + "]";
    }

    private int roleIdByName(String name) {
        for (JsonNode role : parse(get("/api/roles", adminToken)).path("data")) {
            if (name.equals(role.path("name").asText())) {
                return role.path("roleId").asInt();
            }
        }
        throw new IllegalStateException("Role haipo: " + name);
    }
}
