package com.samaki.farm.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import com.samaki.farm.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI A - Auth na RBAC.
 *
 * Hapa ndipo thamani kubwa zaidi ipo: kila skrini ya mfumo inategemea
 * mnyororo huu, na vipande viwili vyake (lango la kubadilisha password,
 * na kuhariri ruhusa za role) vilikuwa VIMEVUNJIKA hadi vilipotengenezwa
 * - bila test yoyote iliyoweza kugundua kama vingevunjika tena.
 */
@DisplayName("A - Auth na RBAC")
class AuthRegressionTest extends IntegrationTest {

    @Nested
    @DisplayName("kuingia")
    class Login {

        @Test
        @DisplayName("password sahihi inatoa token na taarifa za mtumiaji")
        void succeeds() {
            ResponseEntity<String> response = post("/api/auth/login",
                    "{\"phone\":\"" + ADMIN_PHONE + "\",\"password\":\"" + PASSWORD + "\"}", null);
            JsonNode body = parse(response);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(body.path("success").asBoolean()).isTrue();
            assertThat(body.path("data").path("token").asText()).isNotBlank();
            assertThat(body.path("data").path("user").path("phone").asText()).isEqualTo(ADMIN_PHONE);
            assertThat(body.path("data").path("mustChangePassword").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("password batili ni 401 INVALID_CREDENTIALS, si 403")
        void wrongPassword() {
            ResponseEntity<String> response = post("/api/auth/login",
                    "{\"phone\":\"" + ADMIN_PHONE + "\",\"password\":\"si-sahihi\"}", null);
            JsonNode body = parse(response);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            // INVALID_CREDENTIALS, si UNAUTHENTICATED: frontend inaonyesha
            // kosa kwenye fomu badala ya kumtoa mtu kwenye kikao chake.
            assertThat(body.path("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("mtu asiyejulikana anapata jibu LILE LILE la password batili")
        void unknownUser() {
            ResponseEntity<String> response = post("/api/auth/login",
                    "{\"phone\":\"0700999999\",\"password\":\"" + PASSWORD + "\"}", null);

            // Jibu moja kwa zote mbili: vinginevyo endpoint ingekuwa
            // inaorodhesha namba zilizosajiliwa.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(parse(response).path("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("bila token, njia iliyolindwa ni 401 UNAUTHENTICATED")
        void noToken() {
            ResponseEntity<String> response = get("/api/auth/me", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(parse(response).path("errorCode").asText()).isEqualTo("UNAUTHENTICATED");
        }
    }

    @Nested
    @DisplayName("lango la must_change_password")
    class MustChangePasswordGate {

        /**
         * Lango hili LILIKUWA HALIFANYI KAZI: flag ilikuwepo kwenye
         * database lakini hakuna kilichoilazimisha, hivyo mtu aliyepewa
         * password ya muda aliweza kuitumia mfumo mzima bila kuibadilisha.
         */
        @Test
        @DisplayName("mtu aliyewekewa lango anazuiwa kwenye njia za mfumo")
        void gatedUserIsBlockedFromTheApp() {
            raiseGateFor(ADMIN_PHONE);
            String token = login(ADMIN_PHONE);

            ResponseEntity<String> users = get("/api/users?farmId=" + farmA, token);
            assertThat(users.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(parse(users).path("errorCode").asText()).isEqualTo("MUST_CHANGE_PASSWORD");

            // GraphQL pia - lango liko kwenye filter chain, kabla ya
            // resolver yoyote, hivyo halina tundu la API moja.
            ResponseEntity<String> gql = post("/graphql",
                    "{\"query\":\"query { waterQualityLogs { logId } }\"}", token);
            assertThat(gql.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(parse(gql).path("errorCode").asText()).isEqualTo("MUST_CHANGE_PASSWORD");
        }

        /**
         * `/api/auth/**` IMEACHWA NJE ya lango kwa makusudi
         * (JwtAuthFilter.AUTH_PATH_PREFIX): /change-password ndiyo njia ya
         * kutoka, na bila ruhusa hiyo mtu angekwama bila njia ya kujinasua.
         *
         * Imeandikwa kama test kwa sababu ni jambo linaloonekana kama
         * tundu la usalama likiwa halijaelezwa - na kwa sababu frontend
         * ina maoni yanayosema kinyume (AuthService.refreshPermissions
         * inadai /me inakataliwa hapa; SIVYO ilivyo).
         */
        @Test
        @DisplayName("lakini njia za /api/auth zinabaki wazi - ndiyo njia ya kutoka")
        void authPathsStayOpenWhileGated() {
            raiseGateFor(ADMIN_PHONE);
            String token = login(ADMIN_PHONE);

            assertThat(get("/api/auth/me", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("login yenyewe inaruhusiwa, na inatangaza lango")
        void loginStillWorksAndAnnouncesTheGate() {
            raiseGateFor(ADMIN_PHONE);

            JsonNode body = parse(post("/api/auth/login",
                    "{\"phone\":\"" + ADMIN_PHONE + "\",\"password\":\"" + PASSWORD + "\"}", null));

            // Lazima aingie - vinginevyo hangeweza KAMWE kubadilisha
            // password yake. Jibu ndilo linalomwambia afanye hivyo.
            assertThat(body.path("data").path("token").asText()).isNotBlank();
            assertThat(body.path("data").path("mustChangePassword").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("kubadilisha password kunafungua lango na kurudisha ufikiaji")
        void changingPasswordClearsTheGate() {
            raiseGateFor(ADMIN_PHONE);
            String token = login(ADMIN_PHONE);

            ResponseEntity<String> changed = post("/api/auth/change-password",
                    "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Mpya@12345\"}", token);
            assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Token ILE ILE inafungua mfumo sasa: haitolewi upya, na
            // haihitaji kutolewa upya - haibebi chochote kuhusu password.
            ResponseEntity<String> users = get("/api/users?farmId=" + farmA, token);
            assertThat(users.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("password ya sasa ikiwa si sahihi, lango linabaki")
        void wrongCurrentPasswordKeepsTheGate() {
            raiseGateFor(ADMIN_PHONE);
            String token = login(ADMIN_PHONE);

            ResponseEntity<String> response = post("/api/auth/change-password",
                    "{\"currentPassword\":\"si-sahihi\",\"newPassword\":\"Mpya@12345\"}", token);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(parse(response).path("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
            // Lango halijafunguliwa na jaribio lililoshindwa.
            assertThat(get("/api/users?farmId=" + farmA, token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        private void raiseGateFor(String phone) {
            inTx(() -> {
                User user = userRepository.findByPhone(phone).orElseThrow();
                user.setMustChangePassword(true);
                return userRepository.save(user);
            });
            com.samaki.farm.auth.security.JwtAuthFilter.clearAllUserCache();
        }
    }

    @Nested
    @DisplayName("GET /api/auth/me")
    class Me {

        @Test
        @DisplayName("inarudisha ruhusa HALISI za mtumiaji, si jina la role")
        void returnsLivePermissions() {
            JsonNode body = parse(get("/api/auth/me", workerToken)).path("data");

            assertThat(body.path("phone").asText()).isEqualTo(WORKER_PHONE);
            assertThat(body.path("farmId").asInt()).isEqualTo(farmA);

            // Misimbo yenyewe, si jina la role: ndicho frontend inachotawi
            // kwacho, na ndicho kinachobadilika role ikihaririwa.
            assertThat(permissionList(body))
                    .contains("view_dashboard", "log_feeding", "log_water_quality")
                    .doesNotContain("manage_users", "manage_farms");
        }

        @Test
        @DisplayName("mtu asiye na role hana ruhusa hata moja")
        void noRoleMeansNoPermissions() {
            JsonNode body = parse(get("/api/auth/me", noroleToken)).path("data");

            assertThat(permissionList(body)).isEmpty();
            // Bado ni mwanachama wa shamba - "hana role" si "hana shamba".
            assertThat(body.path("farmId").asInt()).isEqualTo(farmA);
        }
    }

    @Nested
    @DisplayName("RBAC inayohaririwa wakati wa run (D-13)")
    class RuntimeEditableRbac {

        /**
         * Hii ndiyo dhana kuu ya muundo mzima: role ni kifurushi cha ruhusa
         * kinachoweza kubadilishwa, hivyo UI HAIPASWI kutawi kwa jina la
         * role. Endpoint inayotimiza hilo (PUT /api/roles/{id}/permissions)
         * ILIKUWA INAANGUKA KWA 500 KILA MARA hadi D-13 ilipotengenezwa -
         * yaani dhana yenyewe haikuwa imewahi kufanya kazi.
         */
        @Test
        @DisplayName("kuongeza ruhusa kwenye role kunaonekana kwenye /me ya mwanachama")
        void grantingAPermissionShowsUpInMe() {
            assertThat(permissionList(parse(get("/api/auth/me", workerToken)).path("data")))
                    .doesNotContain("manage_units");

            int workerRoleId = roleId("WORKER");
            ResponseEntity<String> edit = put("/api/roles/" + workerRoleId + "/permissions",
                    permissionIdsFor("view_dashboard", "log_feeding",
                            "log_water_quality", "manage_units"), adminToken);
            assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Token ile ile, ruhusa mpya: mabadiliko yanafika bila mtu
            // kuingia upya.
            assertThat(permissionList(parse(get("/api/auth/me", workerToken)).path("data")))
                    .contains("manage_units");
        }

        @Test
        @DisplayName("kuondoa ruhusa kunaifunga njia iliyokuwa wazi")
        void revokingAPermissionClosesTheDoor() {
            // Kabla: WORKER anaweza kurekodi kipimo cha maji.
            assertThat(graphqlErrorCode(graphql(workerToken,
                    "mutation { logWaterQuality(input: {unitId: " + unitA + ", ph: 7.0}) { logId } }")))
                    .isNull();

            put("/api/roles/" + roleId("WORKER") + "/permissions",
                    permissionIdsFor("view_dashboard"), adminToken);

            // Baada: ile ile inakataliwa. Hii ndiyo hali ambayo mfumo
            // uliokuwa ukitawi kwa jina la role ungeikosa kabisa.
            assertThat(graphqlErrorCode(graphql(workerToken,
                    "mutation { logWaterQuality(input: {unitId: " + unitA + ", ph: 7.0}) { logId } }")))
                    .isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("ruhusa isiyojulikana inakataa uhariri MZIMA, si sehemu yake")
        void unknownPermissionRejectsTheWholeEdit() {
            ResponseEntity<String> response = put("/api/roles/" + roleId("WORKER") + "/permissions",
                    "[999999]", adminToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(parse(response).path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
            // Role haikuguswa hata kidogo.
            assertThat(permissionList(parse(get("/api/auth/me", workerToken)).path("data")))
                    .contains("view_dashboard", "log_feeding");
        }
    }

    @Nested
    @DisplayName("kukataliwa kwa RBAC")
    class Denial {

        @Test
        @DisplayName("bila manage_users, kuorodhesha wanachama ni 403 FORBIDDEN")
        void missingPermissionIsForbidden() {
            ResponseEntity<String> response = get("/api/users?farmId=" + farmA, workerToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(parse(response).path("errorCode").asText()).isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("mwenye manage_users anapita njia ile ile")
        void holderOfThePermissionPasses() {
            ResponseEntity<String> response = get("/api/users?farmId=" + farmA, adminToken);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(parse(response).path("data")).isNotEmpty();
        }
    }

    // ----------------------------------------------------------- helpers

    private java.util.List<String> permissionList(JsonNode data) {
        java.util.List<String> codes = new java.util.ArrayList<>();
        data.path("permissions").forEach(node -> codes.add(node.asText()));
        return codes;
    }

    private int roleId(String name) {
        JsonNode roles = parse(get("/api/roles", adminToken)).path("data");
        for (JsonNode role : roles) {
            if (name.equals(role.path("name").asText())) {
                return role.path("roleId").asInt();
            }
        }
        throw new IllegalStateException("Role haipo: " + name);
    }

    private String permissionIdsFor(String... codes) {
        JsonNode page = parse(get("/api/roles/permissions?page=0&size=200", adminToken)).path("data");
        JsonNode items = page.isArray() ? page : page.path("content");
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (JsonNode permission : items) {
            for (String code : codes) {
                if (code.equals(permission.path("code").asText())) {
                    ids.add(String.valueOf(permission.path("permissionId").asInt()));
                }
            }
        }
        if (ids.size() != codes.length) {
            throw new IllegalStateException("Ruhusa hazikupatikana zote: " + items);
        }
        return "[" + String.join(",", ids) + "]";
    }
}
