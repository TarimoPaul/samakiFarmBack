package com.samaki.farm.farmuser;

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
 * Mtu mmoja, mashamba mengi.
 *
 * Kabla ya hili, uanachama wa pili ulihifadhiwa lakini haukuwa na kazi:
 * JwtAuthFilter ilichukua `memberships.get(0)` na kiteuzi cha shamba
 * kilikuwa cha ROOT pekee. Test hizi zinashikilia pande zote: kumpa mtu
 * shamba jingine, kumwona kwenye orodha ya mashamba yake, kuhamia shamba
 * hilo (role NA data zikifuata), na mipaka - shamba lisilo lake
 * linapuuzwa.
 */
@DisplayName("Uanachama wa mashamba mengi")
class MultiFarmMembershipTest extends IntegrationTest {

    @Test
    @DisplayName("mtu aliyepo anapewa shamba la pili, na orodha ya mashamba yake inaonyesha yote mawili")
    void existingPersonGetsASecondFarm() {
        assertThat(assignWorkerTo(farmB, "VIEWER").getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode memberships = parse(get("/api/users/" + workerId + "/memberships", adminToken)).path("data");
        assertThat(farmIds(memberships)).containsExactly(farmA, farmB);
        assertThat(memberships.get(1).path("roleName").asText()).isEqualTo("VIEWER");
        assertThat(memberships.get(1).path("farmName").asText()).isEqualTo("Dev Farm B");
    }

    @Test
    @DisplayName("mwenye shamba moja hana kiteuzi; mwenye mawili anacho, na my-farms inayataja")
    void switcherAppearsOnlyWithMoreThanOneFarm() {
        assertThat(parse(get("/api/auth/me", workerToken)).path("data").path("canSelectFarm").asBoolean())
                .isFalse();

        assignWorkerTo(farmB, "VIEWER");

        assertThat(parse(get("/api/auth/me", workerToken)).path("data").path("canSelectFarm").asBoolean())
                .isTrue();
        JsonNode farms = parse(get("/api/auth/my-farms", workerToken)).path("data");
        assertThat(farmIds(farms)).containsExactly(farmA, farmB);
    }

    @Test
    @DisplayName("X-Farm-Id ya shamba lake inabadilisha farmId NA role; bila kichwa anabaki la kwanza")
    void switchingCarriesTheRoleOfThatFarm() {
        assignWorkerTo(farmB, "VIEWER");

        JsonNode home = parse(get("/api/auth/me", workerToken)).path("data");
        assertThat(home.path("farmId").asInt()).isEqualTo(farmA);
        assertThat(home.path("role").asText()).isEqualTo("WORKER");

        JsonNode switched = parse(getInFarm("/api/auth/me", workerToken, farmB)).path("data");
        assertThat(switched.path("farmId").asInt()).isEqualTo(farmB);
        assertThat(switched.path("role").asText()).isEqualTo("VIEWER");
        assertThat(permissions(switched)).doesNotContain("log_water_quality");
    }

    @Test
    @DisplayName("data inafuata shamba lililochaguliwa: WORKER wa B anaandika B, hawezi A akiwa B")
    void productionDataFollowsTheSelectedFarm() {
        assignWorkerTo(farmB, "WORKER");

        JsonNode onB = graphqlInFarm(workerToken,
                "mutation { logWaterQuality(input: {unitId: " + unitB + ", ph: 7.0}) { logId } }", farmB);
        assertThat(graphqlErrorCode(onB)).isNull();

        JsonNode unitAFromB = graphqlInFarm(workerToken,
                "mutation { logWaterQuality(input: {unitId: " + unitA + ", ph: 7.0}) { logId } }", farmB);
        assertThat(graphqlErrorCode(unitAFromB)).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("shamba asilo mwanachama wake linapuuzwa - hata kwa mwenye manage_farms")
    void aFarmThatIsNotTheirsIsIgnored() {
        JsonNode worker = parse(getInFarm("/api/auth/me", workerToken, farmB)).path("data");
        assertThat(worker.path("farmId").asInt()).isEqualTo(farmA);

        // Dev Admin ana manage_farms (OWNER), lakini hiyo ni ruhusa ya
        // kusimamia wanachama, SI ya kuingia kwenye data ya shamba jingine.
        JsonNode admin = parse(getInFarm("/api/auth/me", adminToken, farmB)).path("data");
        assertThat(admin.path("farmId").asInt()).isEqualTo(farmA);
    }

    @Test
    @DisplayName("aliyetolewa shambani anaweza kurudishwa kwenye shamba lile lile")
    void aRemovedMemberCanBeAddedBack() {
        assertThat(delete("/api/users/" + workerId + "/memberships/" + farmA, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> readd = assignWorkerTo(farmA, "VIEWER");

        assertThat(readd.getStatusCode()).as(readd.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode members = parse(get("/api/users?farmId=" + farmA, adminToken)).path("data");
        assertThat(members).anySatisfy(node -> {
            assertThat(node.path("phone").asText()).isEqualTo(WORKER_PHONE);
            assertThat(node.path("role").asText()).isEqualTo("VIEWER");
        });
        // Na uanachama uliorudishwa unafanya kazi kweli - si safu tu.
        assertThat(parse(get("/api/auth/me", workerToken)).path("data").path("role").asText())
                .isEqualTo("VIEWER");
    }

    @Test
    @DisplayName("lookup kwa simu inampata mtu aliyepo; namba isiyojulikana ni 400")
    void lookupFindsAnExistingPerson() {
        JsonNode found = parse(get("/api/users/lookup?phone=" + WORKER_B_PHONE, adminToken)).path("data");
        assertThat(found.path("name").asText()).isEqualTo("Dev Worker B");
        assertThat(found.path("farmId").isNull()).isTrue();

        ResponseEntity<String> missing = get("/api/users/lookup?phone=0799999999", adminToken);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parse(missing).path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");

        assertThat(get("/api/users/lookup?phone=" + WORKER_B_PHONE, workerToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("msimamizi wa shamba moja: anamuongeza mtu aliyepo shambani kwake, na anaona uanachama wa shamba lake tu")
    void farmLevelAdminAddsExistingPersonToOwnFarmOnly() {
        java.util.UUID workerB = userRepository.findByPhone(WORKER_B_PHONE).orElseThrow().getUserId();
        put("/api/roles/" + roleIdByName("OWNER") + "/permissions",
                permissionIdsFor("manage_users", "view_dashboard"), adminToken);
        String confined = login(ADMIN_PHONE);

        ResponseEntity<String> added = post("/api/users/" + workerB + "/memberships",
                "{\"farmId\":" + farmA + ",\"roleId\":" + roleIdByName("WORKER") + "}", confined);
        assertThat(added.getStatusCode()).as(added.getBody()).isEqualTo(HttpStatus.OK);

        // Worker B sasa yuko A na B, lakini msimamizi wa A anaona A pekee.
        // (Mtazamo kamili wa msimamizi wa kampuni: existingPersonGetsASecondFarm.)
        JsonNode seen = parse(get("/api/users/" + workerB + "/memberships", confined)).path("data");
        assertThat(farmIds(seen)).containsExactly(farmA);
    }

    // ------------------------------------------------------------ helpers

    private ResponseEntity<String> assignWorkerTo(int farmId, String roleName) {
        return post("/api/users/" + workerId + "/memberships",
                "{\"farmId\":" + farmId + ",\"roleId\":" + roleIdByName(roleName) + "}", adminToken);
    }

    private List<Integer> farmIds(JsonNode rows) {
        List<Integer> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.path("farmId").asInt()));
        return ids;
    }

    private List<String> permissions(JsonNode me) {
        List<String> codes = new ArrayList<>();
        me.path("permissions").forEach(code -> codes.add(code.asText()));
        return codes;
    }

    private String permissionIdsFor(String... codes) {
        JsonNode page = parse(get("/api/roles/permissions?page=0&size=200", adminToken)).path("data");
        List<String> ids = new ArrayList<>();
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
