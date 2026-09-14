package com.samaki.farm.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashibodi YA TAREHE - "shamba lilikuwaje siku ile".
 *
 * KWA NINI HAKUNA JEDWALI LA HISTORIA. Swali la kwanza lililoulizwa lilikuwa
 * "tuongeze unit_status_history na created_at?", na jibu ni hapana: data
 * tayari ipo.
 *
 *   * Kitengo kilikuwepo lini  -> `created_at`/`deleted_at` za BaseEntity
 *     (V2 kwa production_units). Soft-delete ndiyo inayofanya hili liwezekane:
 *     kitengo kilichofutwa JANA bado kilikuwepo WIKI ILIYOPITA, na rekodi yake
 *     haijaondoka.
 *   * Kitengo kilikuwa ACTIVE  -> kilikuwa na mzunguko unaoendelea. Hii si
 *     makadirio: status ya kitengo inaandikwa na CycleService PEKEE - "ACTIVE"
 *     mzunguko unapoanza, "IDLE" wa mwisho unapofungwa - na hakuna msimbo
 *     wowote unaoandika "MAINTENANCE".
 *
 * Matokeo ni kwamba historia inafanya kazi kwa tarehe ZA NYUMA. Jedwali la
 * kukusanya lingeanza siku tunapoliwasha, na tarehe zote za kabla yake
 * zingebaki tupu milele. Majaribio hapa chini yanabana hilo: yanauliza kuhusu
 * SIKU ZILIZOPITA za mzunguko ulioanzishwa na harness.
 */
@DisplayName("Dashibodi ya tarehe")
class DashboardOnDateTest extends IntegrationTest {

    private JsonNode onDate(String token, String date) {
        return graphql(token, "query { dashboardOnDate(date: \"" + date + "\") { "
                + "date unitsExisting unitsActive unitsIdle totalVolumeM3 "
                + "cyclesRunning cyclesStarted cyclesClosed "
                + "fingerlingsRunning fingerlingsStocked members unitsByType { type count } "
                + "historyStartsOn historyComplete } }")
                .path("data").path("dashboardOnDate");
    }

    @Test
    @DisplayName("inarudisha tarehe iliyoulizwa, si ya leo")
    void answersForTheDateAsked() {
        String yesterday = LocalDate.now().minusDays(1).toString();

        JsonNode day = onDate(adminToken, yesterday);

        assertThat(day.path("date").asText()).isEqualTo(yesterday);
    }

    @Test
    @DisplayName("kitengo kilicho na mzunguko unaoendelea kinahesabiwa ACTIVE siku hiyo")
    void countsAUnitWithARunningCycleAsActive() {
        // Mzunguko wa harness (cycleA) uko ACTIVE na haujafungwa, hivyo leo
        // kitengo chake lazima kihesabiwe.
        JsonNode today = onDate(adminToken, LocalDate.now().toString());

        assertThat(today.path("unitsActive").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(today.path("cyclesRunning").asLong()).isGreaterThanOrEqualTo(1);
        // IDLE ni kilichobaki - jumla lazima ifunge.
        assertThat(today.path("unitsIdle").asLong() + today.path("unitsActive").asLong())
                .isEqualTo(today.path("unitsExisting").asLong());
    }

    @Test
    @DisplayName("vifaranga wa mizunguko inayoendelea ni tofauti na walioingizwa siku hiyo")
    void keepsStockAndIntakeApart() {
        // Mzunguko wa harness uliwekwa SIKU ILIYOPITA, si leo. Kwa hiyo leo:
        // hifadhi ipo (mzunguko bado unaendelea), lakini mtiririko wa leo ni
        // sifuri. Tile ya dashibodi inasoma HIFADHI - hizi mbili zikichanganywa,
        // namba ingebadilisha maana bila mtu kujua.
        JsonNode today = onDate(adminToken, LocalDate.now().toString());

        assertThat(today.path("fingerlingsRunning").asLong()).isPositive();
        assertThat(today.path("fingerlingsStocked").asLong())
                .isNotEqualTo(today.path("fingerlingsRunning").asLong());
    }

    @Test
    @DisplayName("vitengo vinagawanywa kwa aina, na jumla inafunga")
    void breaksTheUnitsDownByType() {
        JsonNode today = onDate(adminToken, LocalDate.now().toString());
        JsonNode byType = today.path("unitsByType");

        assertThat(byType.isArray()).isTrue();
        assertThat(byType).isNotEmpty();

        // Jumla ya aina zote lazima iwe sawa na vitengo vilivyokuwepo -
        // vinginevyo mgawanyo ungekuwa unaeleza kitu kingine na si kile kile
        // kadi ya juu yake inachohesabu.
        long summed = 0;
        for (JsonNode row : byType) {
            assertThat(row.path("type").asText()).isNotBlank();
            summed += row.path("count").asLong();
        }
        assertThat(summed).isEqualTo(today.path("unitsExisting").asLong());
    }

    @Test
    @DisplayName("siku KABLA ya kuwekwa kwa vifaranga, mzunguko hauhesabiwi")
    void doesNotCountACycleBeforeItWasStocked() {
        // Siku moja kabla ya mzunguko kuanza. Hii ndiyo hoja nzima ya kukokotoa
        // badala ya kukusanya: tarehe hii ilipita kabla kipengele hiki
        // hakijaandikwa, na bado ina jibu sahihi.
        String beforeStocking = LocalDate.now().minusYears(5).toString();

        JsonNode day = onDate(adminToken, beforeStocking);

        assertThat(day.path("cyclesRunning").asLong()).isZero();
        assertThat(day.path("unitsActive").asLong()).isZero();
    }

    @Test
    @DisplayName("tarehe iliyo kabla ya rekodi yetu inatiwa alama, haidai shamba lilikuwa tupu")
    void marksDatesBeforeOurRecordsAsIncomplete() {
        JsonNode old = onDate(adminToken, LocalDate.now().minusYears(5).toString());

        // Sifuri hapa ina maana ya "hatujui", si "hapakuwa na kitu" - na jibu
        // linasema hivyo lenyewe badala ya kuiacha UI ikisie.
        assertThat(old.path("historyComplete").asBoolean()).isFalse();
        assertThat(old.path("historyStartsOn").isNull()).isFalse();

        JsonNode today = onDate(adminToken, LocalDate.now().toString());
        assertThat(today.path("historyComplete").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("tarehe isiyo sahihi inakataliwa kwa ujumbe unaoeleweka")
    void rejectsAMalformedDate() {
        JsonNode answer = graphql(adminToken,
                "query { dashboardOnDate(date: \"07-09-2026\") { date } }");

        assertThat(answer.path("errors").isEmpty()).isFalse();
        assertThat(answer.path("errors").get(0).path("message").asText())
                .contains("2026-09-07");
    }

    @Test
    @DisplayName("inadai view_dashboard na muktadha wa shamba")
    void requiresViewDashboardAndAFarm() {
        // NOROLE hana ruhusa yoyote - swali lile lile linakataliwa.
        JsonNode answer = graphql(noroleToken,
                "query { dashboardOnDate(date: \"" + LocalDate.now() + "\") { date } }");

        assertThat(answer.path("errors").isEmpty()).isFalse();
    }
}
