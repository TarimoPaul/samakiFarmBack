package com.samaki.farm.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PUT /api/users/{userId} - jina, simu, barua pepe.
 *
 * Endpoint hii ilikuwa HAIPO kabisa: mtu aliyeandikishwa vibaya alibaki
 * hivyo hivyo milele, au alilazimika kufutwa na kuundwa upya - jambo
 * linalopoteza uanachama wake na historia yake.
 *
 * Mipaka yake ndiyo hasa inayojaribiwa hapa: inagusa utambulisho PEKEE.
 * Password, hali ya akaunti na uanachama vina endpoint zao, na ombi la
 * kurekebisha herufi moja ya jina halipaswi kugusa kile mtu anachoweza
 * kufanya.
 */
@DisplayName("Mtu - kuhariri utambulisho wake")
class UserProfileRegressionTest extends IntegrationTest {

    private static final String NEW_PHONE = "0700900001";

    @Test
    @DisplayName("jina na simu vinabadilika, na orodha ya shamba inaonyesha mapya")
    void nameAndPhoneAreUpdated() {
        ResponseEntity<String> response = put("/api/users/" + workerId,
                body("Mfanyakazi Mpya", NEW_PHONE, null), adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode member = memberById(workerId.toString());
        assertThat(member.path("name").asText()).isEqualTo("Mfanyakazi Mpya");
        assertThat(member.path("phone").asText()).isEqualTo(NEW_PHONE);
    }

    @Test
    @DisplayName("nafasi na hali ya akaunti HAZIGUSWI - ni endpoint za wenzao")
    void roleAndStatusAreUntouched() {
        JsonNode before = memberById(workerId.toString());

        put("/api/users/" + workerId, body("Jina Jipya", NEW_PHONE, null), adminToken);

        JsonNode after = memberById(workerId.toString());
        assertThat(after.path("role").asText()).isEqualTo(before.path("role").asText());
        assertThat(after.path("status").asText()).isEqualTo(before.path("status").asText());
        assertThat(after.path("farmId").asInt()).isEqualTo(before.path("farmId").asInt());
    }

    @Test
    @DisplayName("simu mpya inatumika kuingia; ya zamani haitumiki tena")
    void theNewPhoneIsTheOneThatSignsIn() {
        put("/api/users/" + workerId, body("F Worker", NEW_PHONE, null), adminToken);

        // Utambulisho ndio unaobadilika, si kikao: token ya zamani haiathiriwi
        // (JwtAuthFilter inahifadhi userId, si namba), lakini login mpya ni
        // kwa namba mpya.
        ResponseEntity<String> withNew = post("/api/auth/login",
                "{\"phone\":\"" + NEW_PHONE + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(withNew.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> withOld = post("/api/auth/login",
                "{\"phone\":\"" + WORKER_PHONE + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(withOld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("jina tupu: 400 VALIDATION_ERROR")
    void blankNameIsRefused() {
        ResponseEntity<String> response =
                put("/api/users/" + workerId, body("   ", NEW_PHONE, null), adminToken);
        JsonNode json = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.path("message").asText()).contains("Jina linahitajika");
    }

    @Test
    @DisplayName("namba ya mtu mwingine: 409 ikitaja namba, si vikwazo vya database")
    void aTakenPhoneIsNamed() {
        ResponseEntity<String> response =
                put("/api/users/" + workerId, body("F Worker", ADMIN_PHONE, null), adminToken);
        JsonNode json = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.path("message").asText())
                .isEqualTo("Namba ya simu hii tayari imesajiliwa.");
        assertThat(json.path("message").asText()).doesNotContain("vikwazo vya database");
    }

    @Test
    @DisplayName("kubakiza namba yake mwenyewe si rudufu")
    void keepingItsOwnPhoneIsNotADuplicate() {
        ResponseEntity<String> response =
                put("/api/users/" + workerId, body("Jina Jipya Tu", WORKER_PHONE, null), adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberById(workerId.toString()).path("name").asText()).isEqualTo("Jina Jipya Tu");
    }

    @Test
    @DisplayName("namba ya mtu ALIYEFUTWA bado imechukuliwa, na inasemwa hivyo")
    void aDeletedPersonStillOwnsTheirPhone() {
        String created = parse(post("/api/users",
                "{\"name\":\"Wa Muda\",\"phone\":\"0700900777\",\"password\":\"secret123\"}",
                adminToken)).path("data").path("id").asText();

        assertThat(delete("/api/users/" + created, adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Safu iliyofutwa bado ipo na `users.phone` ni UNIQUE. Bila swali la
        // native lililoiona, hii ingekuwa DataIntegrityViolation - 409 yenye
        // sentensi ya jumla ya database badala ya jibu la kweli.
        ResponseEntity<String> again = put("/api/users/" + workerId,
                body("F Worker", "0700900777", null), adminToken);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parse(again).path("message").asText())
                .isEqualTo("Namba ya simu hii tayari imesajiliwa.");
    }

    @Test
    @DisplayName("barua pepe tupu inahifadhiwa kama NULL - hivyo wengi wanaweza kutokuwa nayo")
    void ablankEmailBecomesNull() {
        // Wawili bila barua pepe. Ikihifadhiwa kama "" badala ya NULL, wa pili
        // angekataliwa na kikwazo cha UNIQUE kwa sababu isiyoeleweka kabisa.
        ResponseEntity<String> first = post("/api/users",
                "{\"name\":\"Bila Barua A\",\"phone\":\"0700900801\",\"email\":\"\",\"password\":\"secret123\"}",
                adminToken);
        ResponseEntity<String> second = post("/api/users",
                "{\"name\":\"Bila Barua B\",\"phone\":\"0700900802\",\"email\":\"\",\"password\":\"secret123\"}",
                adminToken);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Na kuhariri kuiacha tupu kunafanya vivyo hivyo.
        String id = parse(second).path("data").path("id").asText();
        assertThat(put("/api/users/" + id, body("Bila Barua B", "0700900802", ""), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("msimamizi ANAWEZA kujihariri mwenyewe - tofauti na kujizima au kujifuta")
    void theAdminMayEditThemselves() {
        ResponseEntity<String> response =
                put("/api/users/" + adminId, body("Msimamizi Mkuu", ADMIN_PHONE, null), adminToken);

        // Kujizima na kujifuta kunakataliwa (400) kwa sababu kunajifungia nje.
        // Kubadilisha jina lako hakufanyi hivyo, hivyo kikwazo kingekuwa cha
        // kubuni - angalia UserService.updateUser.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberById(adminId.toString()).path("name").asText()).isEqualTo("Msimamizi Mkuu");
    }

    @Test
    @DisplayName("inahitaji manage_users")
    void itNeedsManageUsers() {
        ResponseEntity<String> response =
                put("/api/users/" + workerId, body("Kwa Nguvu", NEW_PHONE, null), workerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -------------------------------------------------------------- msaidizi

    private static String body(String name, String phone, String email) {
        return "{\"name\":\"" + name + "\",\"phone\":\"" + phone + "\""
                + (email == null ? "" : ",\"email\":\"" + email + "\"") + "}";
    }

    private JsonNode memberById(String userId) {
        for (JsonNode member : parse(get("/api/users?farmId=" + farmA, adminToken)).path("data")) {
            if (userId.equals(member.path("id").asText())) {
                return member;
            }
        }
        throw new IllegalStateException("Mwanachama hayupo kwenye orodha: " + userId);
    }
}
