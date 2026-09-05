package com.samaki.farm.rbac;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Maisha ya nafasi (role): kuunda, kuhariri, KUZIMA na KUFUTA.
 *
 * Kuzima na kufuta ni vitendo VIWILI tofauti, na tofauti yao ndiyo hasa
 * inayojaribiwa hapa - kwa sababu ndiyo rahisi kuivunja bila kuionekana:
 *
 *   kuzima  huwaacha walioshikilia wakiwa na kila kitu, na huzuia
 *           KUPEWA mtu mpya tu;
 *   kufuta  hukataliwa kabisa endapo mtu yeyote bado anaishikilia.
 *
 * Zote mbili zikitegemea soft-delete pekee, "kuzima" kungeifanya nafasi
 * itoweke kwenye skrini pekee inayoweza kuirudisha - na kufuta bila
 * ukaguzi kungewanyang'anya watu ruhusa zao kimyakimya, kwa sababu
 * `farm_users.role_id` haina ON DELETE CASCADE na soft-delete inaacha
 * safu ikielekeza kwenye kitu kilichofichwa.
 */
@DisplayName("Nafasi - kuunda, kuhariri, kuzima, kufuta")
class RoleLifecycleRegressionTest extends IntegrationTest {

    // ------------------------------------------------------------- kuunda

    @Test
    @DisplayName("jina tupu: 400 VALIDATION_ERROR, si 409 ya jumla ya database")
    void blankNameIsRefusedWithSomethingActionable() {
        ResponseEntity<String> response = post("/api/roles",
                "{\"name\":\"   \",\"description\":\"x\",\"permissionIds\":[]}", adminToken);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        // Awali hii ilifika database na kurudi kama DataIntegrityViolation -
        // yaani sentensi ya jumla kuhusu "vikwazo vya database", isiyomwambia
        // msimamizi kwamba tatizo ni jina.
        assertThat(body.path("message").asText()).isEqualTo("Jina la nafasi linahitajika.");
    }

    @Test
    @DisplayName("jina linalojirudia: 409 + sentensi inayotaja jina, si vikwazo vya database")
    void duplicateNameIsNamed() {
        ResponseEntity<String> response = post("/api/roles",
                "{\"name\":\"OWNER\",\"description\":null,\"permissionIds\":[]}", adminToken);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.path("message").asText()).isEqualTo("Nafasi yenye jina hili tayari ipo.");
        assertThat(body.path("message").asText()).doesNotContain("vikwazo vya database");
    }

    @Test
    @DisplayName("nafasi mpya inaanza ikiwa hai")
    void aNewRoleStartsActive() {
        JsonNode created = createRole("MHASIBU", "Anayeshughulikia fedha");

        assertThat(created.path("active").asBoolean()).isTrue();
        assertThat(created.path("name").asText()).isEqualTo("MHASIBU");
        assertThat(created.path("description").asText()).isEqualTo("Anayeshughulikia fedha");
    }

    // ------------------------------------------------------------ kuhariri

    @Test
    @DisplayName("PUT inabadilisha jina na maelezo, na HAIGUSI ruhusa")
    void updateChangesNameAndDescriptionOnly() {
        int workerRole = roleIdByName("WORKER");
        int permissionsBefore = roleByName("WORKER").path("permissions").size();
        assertThat(permissionsBefore).isGreaterThan(0);

        ResponseEntity<String> response = put("/api/roles/" + workerRole,
                "{\"name\":\"MFANYAKAZI\",\"description\":\"Kazi za kila siku\"}", adminToken);
        JsonNode body = parse(response).path("data");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body.path("name").asText()).isEqualTo("MFANYAKAZI");
        // Ruhusa zipo pale pale: DTO ya PUT haina uwanja wa ruhusa kwa
        // makusudi, ili ombi la kubadilisha jina lisiweze kufuta sera nzima.
        assertThat(body.path("permissions").size()).isEqualTo(permissionsBefore);
    }

    @Test
    @DisplayName("kuhifadhi nafasi bila kubadilisha jina lake si rudufu")
    void keepingItsOwnNameIsNotADuplicate() {
        int workerRole = roleIdByName("WORKER");

        ResponseEntity<String> response = put("/api/roles/" + workerRole,
                "{\"name\":\"WORKER\",\"description\":\"Maelezo mapya\"}", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(response).path("data").path("description").asText())
                .isEqualTo("Maelezo mapya");
    }

    // -------------------------------------------------------------- kuzima

    @Test
    @DisplayName("iliyozimwa INABAKI kwenye orodha - ndipo pekee inaporudishwa")
    void aDeactivatedRoleStaysVisible() {
        int roleId = createRole("MHASIBU", null).path("roleId").asInt();

        post("/api/roles/" + roleId + "/deactivate", null, adminToken);

        JsonNode role = roleByName("MHASIBU");
        assertThat(role.isMissingNode()).isFalse();
        assertThat(role.path("active").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("iliyozimwa haipewi mtu mpya, na ujumbe unaeleza njia ya kutoka")
    void aDeactivatedRoleCannotBeAssigned() {
        int viewerRole = roleIdByName("VIEWER");
        post("/api/roles/" + viewerRole + "/deactivate", null, adminToken);

        ResponseEntity<String> response = put(
                "/api/users/" + workerId + "/memberships/" + farmA + "/role",
                "{\"farmId\":" + farmA + ",\"roleId\":" + viewerRole + "}", adminToken);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.path("message").asText())
                .contains("imezimwa")
                .contains("Irudishe kwanza");
    }

    @Test
    @DisplayName("kuzima HAKUMGUSI aliyekwisha kuwa nayo - ndiyo tofauti yake na kufuta")
    void deactivationLeavesExistingHoldersAlone() {
        int workerRole = roleIdByName("WORKER");
        JsonNode before = parse(get("/api/auth/me", workerToken)).path("data");
        assertThat(before.path("permissions").size()).isGreaterThan(0);

        post("/api/roles/" + workerRole + "/deactivate", null, adminToken);

        JsonNode after = parse(get("/api/auth/me", workerToken)).path("data");
        assertThat(after.path("role").asText()).isEqualTo(before.path("role").asText());
        assertThat(after.path("permissions")).isEqualTo(before.path("permissions"));
    }

    @Test
    @DisplayName("kurudisha kunairejesha kwenye matumizi")
    void activatingMakesItAssignableAgain() {
        int viewerRole = roleIdByName("VIEWER");
        post("/api/roles/" + viewerRole + "/deactivate", null, adminToken);
        post("/api/roles/" + viewerRole + "/activate", null, adminToken);

        ResponseEntity<String> response = put(
                "/api/users/" + workerId + "/memberships/" + farmA + "/role",
                "{\"farmId\":" + farmA + ",\"roleId\":" + viewerRole + "}", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roleByName("VIEWER").path("active").asBoolean()).isTrue();
    }

    // -------------------------------------------------------------- kufuta

    @Test
    @DisplayName("nafasi isiyoshikiliwa na mtu inafutwa, na inatoweka kwenye orodha")
    void anUnheldRoleIsDeleted() {
        int roleId = createRole("MHASIBU", null).path("roleId").asInt();

        ResponseEntity<String> response = delete("/api/roles/" + roleId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roleByName("MHASIBU").isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("nafasi inayoshikiliwa: 409 + ROLE_IN_USE, ikitaja idadi, na haiguswi")
    void aHeldRoleIsRefused() {
        int workerRole = roleIdByName("WORKER");

        ResponseEntity<String> response = delete("/api/roles/" + workerRole, adminToken);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.path("errorCode").asText()).isEqualTo("ROLE_IN_USE");
        // Idadi ndiyo inayoifanya ROLE_IN_USE iwe na thamani zaidi ya
        // CONFLICT ya jumla: inamwambia msimamizi kilichobaki kufanywa.
        assertThat(body.path("message").asText()).contains("inashikiliwa na watu");
        assertThat(body.path("message").asText()).contains("izime badala ya kuifuta");

        // Na muhimu zaidi: nafasi bado ipo, na mwenyewe hajaguswa.
        assertThat(roleByName("WORKER").isMissingNode()).isFalse();
        assertThat(parse(get("/api/auth/me", workerToken)).path("data").path("permissions").size())
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("ROLE_IN_USE inapita mara watu wanapohamishwa - si kikwazo cha kudumu")
    void theRefusalCanBeCleared() {
        // Nafasi mpya, mshikiliaji MMOJA anayejulikana. Kutumia WORKER hapa
        // hakungefanya kazi na hiyo ni sahihi: fixture ina wafanyakazi
        // wawili (shamba A na B), hivyo kumhamisha mmoja huacha mwingine -
        // na kikwazo kinaendelea kwa usahihi kabisa.
        int spare = createRole("MHASIBU", null).path("roleId").asInt();
        int workerRole = roleIdByName("WORKER");

        put("/api/users/" + workerId + "/memberships/" + farmA + "/role",
                "{\"farmId\":" + farmA + ",\"roleId\":" + spare + "}", adminToken);

        assertThat(delete("/api/roles/" + spare, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // Mrudishe, kisha jaribu tena - ombi lilelile, sasa linapita.
        put("/api/users/" + workerId + "/memberships/" + farmA + "/role",
                "{\"farmId\":" + farmA + ",\"roleId\":" + workerRole + "}", adminToken);

        assertThat(delete("/api/roles/" + spare, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("kufuta mara ya pili ni 400 - findById peke yake ingesema imefanikiwa")
    void deletingTwiceIsRefused() {
        int roleId = createRole("MHASIBU", null).path("roleId").asInt();
        delete("/api/roles/" + roleId, adminToken);

        ResponseEntity<String> second = delete("/api/roles/" + roleId, adminToken);

        // Hibernate haitumii @SQLRestriction kwenye lookup ya PK, hivyo bila
        // ukaguzi wa isDeleted() ndani ya requireRole hii ingekuwa 200.
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parse(second).path("message").asText()).isEqualTo("Role haipo");
    }

    @Test
    @DisplayName("iliyofutwa haipewi mtu, hata kwa kitambulisho chake cha zamani")
    void aDeletedRoleCannotBeAssigned() {
        int roleId = createRole("MHASIBU", null).path("roleId").asInt();
        delete("/api/roles/" + roleId, adminToken);

        ResponseEntity<String> response = put(
                "/api/users/" + workerId + "/memberships/" + farmA + "/role",
                "{\"farmId\":" + farmA + ",\"roleId\":" + roleId + "}", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parse(response).path("message").asText()).isEqualTo("Role haipo");
    }

    @Test
    @DisplayName("jina la nafasi iliyofutwa bado limechukuliwa, na inasemwa hivyo")
    void aDeletedRoleStillOwnsItsName() {
        int roleId = createRole("MHASIBU", null).path("roleId").asInt();
        delete("/api/roles/" + roleId, adminToken);

        ResponseEntity<String> response = post("/api/roles",
                "{\"name\":\"MHASIBU\",\"description\":null,\"permissionIds\":[]}", adminToken);

        // `roles.name` ni UNIQUE na safu iliyofutwa bado ipo. Bila swali la
        // native lililoiona, hii ingekuwa DataIntegrityViolation - 409 yenye
        // sentensi ya jumla ya database badala ya jibu la kweli.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parse(response).path("message").asText())
                .isEqualTo("Nafasi yenye jina hili tayari ipo.");
    }

    // --------------------------------------------------------------- ruhusa

    @Test
    @DisplayName("vitendo vyote vinahitaji manage_users")
    void everyActionNeedsManageUsers() {
        int roleId = roleIdByName("VIEWER");

        // WORKER ana view_dashboard/mark_task_done/log_feeding - si manage_users.
        assertThat(post("/api/roles", "{\"name\":\"X\",\"permissionIds\":[]}", workerToken)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(put("/api/roles/" + roleId, "{\"name\":\"X\"}", workerToken)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/roles/" + roleId + "/deactivate", null, workerToken)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete("/api/roles/" + roleId, workerToken)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -------------------------------------------------------------- msaidizi

    private JsonNode createRole(String name, String description) {
        String body = "{\"name\":\"" + name + "\",\"description\":"
                + (description == null ? "null" : "\"" + description + "\"")
                + ",\"permissionIds\":[]}";
        ResponseEntity<String> response = post("/api/roles", body, adminToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parse(response).path("data");
    }

    /** Nafasi kwa jina kutoka GET /api/roles, au node inayokosekana. */
    private JsonNode roleByName(String name) {
        for (JsonNode role : parse(get("/api/roles", adminToken)).path("data")) {
            if (name.equals(role.path("name").asText())) {
                return role;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private int roleIdByName(String name) {
        JsonNode role = roleByName(name);
        if (role.isMissingNode()) {
            throw new IllegalStateException("Nafasi haipo kwenye fixture: " + name);
        }
        return role.path("roleId").asInt();
    }
}
