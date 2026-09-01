package com.samaki.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import com.samaki.farm.support.TestDatabase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Harness yenyewe: database ya majaribio, migrations, fixture, na token.
 *
 * Kama hii ni nyekundu, hakuna test nyingine yenye maana - ndiyo maana
 * inajaribu mnyororo mzima badala ya kuamini kwamba umefanya kazi.
 */
@DisplayName("Harness")
class HarnessSmokeTest extends IntegrationTest {

    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("inaendesha juu ya database ya majaribio inayotupwa, si ya dev wala prod")
    void runsOnAThrowawayDatabase() {
        String actual = inTx(() -> (String) entityManager
                .createNativeQuery("SELECT current_database()").getSingleResult());

        assertThat(actual).isEqualTo(TestDatabase.databaseName());
        assertThat(actual).startsWith("samaki_test_");
        // Ulinzi wa moja kwa moja dhidi ya kosa baya zaidi linalowezekana
        // hapa: majaribio yanayofuta majedwali ya database halisi.
        assertThat(actual).isNotEqualTo("samakiFarm");
    }

    @Test
    @DisplayName("Flyway imeendesha V1 hadi ya mwisho kutoka utupu, bila kuruka toleo")
    void migrationsRanFromScratch() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = inTx(() -> (List<Object[]>) entityManager.createNativeQuery(
                "SELECT version, description, success FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL ORDER BY installed_rank").getResultList());

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> assertThat(row[2]).isEqualTo(true));

        List<String> versions = rows.stream().map(row -> String.valueOf(row[0])).toList();
        assertThat(versions).startsWith("1");
        // Mnyororo mzima, si toleo la mwisho pekee: database mpya kabisa
        // LAZIMA ipite kila migration kwa mpangilio.
        assertThat(versions).containsSequence("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
    }

    @Test
    @DisplayName("wahusika wa fixture wanaingia na kupata token")
    void everySeededPrincipalCanLogIn() {
        assertThat(adminToken).isNotBlank();
        assertThat(workerToken).isNotBlank();
        assertThat(viewerToken).isNotBlank();
        assertThat(noroleToken).isNotBlank();
        assertThat(workerBToken).isNotBlank();

        ResponseEntity<String> response = post("/api/auth/login",
                "{\"phone\":\"" + ADMIN_PHONE + "\",\"password\":\"" + PASSWORD + "\"}", null);
        JsonNode body = parse(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("user").path("name").asText()).isEqualTo("Dev Admin");
        assertThat(body.path("data").path("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("fixture ina mashamba mawili - bila hilo D-1 haiwezi kuthibitishwa")
    void fixtureHasTwoFarms() {
        assertThat(farmA).isNotEqualTo(farmB);
        assertThat(unit(unitA).getFarm().getFarmId()).isEqualTo(farmA);
        assertThat(unit(unitB).getFarm().getFarmId()).isEqualTo(farmB);
    }

    @Test
    @DisplayName("kila test inaanza na fixture safi (sehemu ya 1/2: inaandika)")
    void isolationPartOneWrites() {
        JsonNode response = graphql(workerToken,
                "mutation { logWaterQuality(input: {unitId: " + unitA + ", ph: 7.0}) { logId } }");
        assertThat(graphqlErrorCode(response)).isNull();

        JsonNode all = graphql(workerToken, "query { waterQualityLogs { logId } }");
        assertThat(all.path("data").path("waterQualityLogs")).hasSize(1);
    }

    @Test
    @DisplayName("kila test inaanza na fixture safi (sehemu ya 2/2: haioni ya kwanza)")
    void isolationPartTwoSeesNothing() {
        // Kama kutenganisha kunafanya kazi, kipimo cha test iliyotangulia
        // hakipo hapa - hata kama ilikimbia kabla yake.
        JsonNode all = graphql(workerToken, "query { waterQualityLogs { logId } }");
        assertThat(all.path("data").path("waterQualityLogs")).isEmpty();
    }
}
