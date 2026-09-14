package com.samaki.farm.cycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.species.repository.SpeciesRepository;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUREKEBISHA IDADI YA VIFARANGA - correctFingerlingsCount.
 *
 * Idadi ya siku ya kupanda ni makadirio; matukio ya mavuno yakiizidi,
 * mkulima anahitaji njia ya kuirekebisha. Faili hili linalinda mipaka yake:
 * mzunguko UNAOENDELEA pekee, idadi > 0, `edit_cycle`, shamba la mwombaji.
 */
@DisplayName("C - Kurekebisha idadi ya vifaranga")
class FingerlingsCorrectionTest extends IntegrationTest {

    @Autowired private SpeciesRepository species;
    @Autowired private CycleRepository cycles;
    @Autowired private ProductionUnitRepository units;

    /** Mzunguko wa shamba A, uliowekwa 2025-01-10, vifaranga 100. */
    private int cycleId;

    @BeforeEach
    void stockCycle() {
        int speciesId = species.findAll().get(0).getSpeciesId();
        JsonNode res = graphql(adminToken, "mutation { createCycle(input: {unitId: " + unitA
                + ", speciesId: " + speciesId + ", stockingDate: \"2025-01-10\""
                + ", fingerlingsCount: 100}) { cycleId } }");
        assertThat(graphqlErrorCode(res)).isNull();
        cycleId = res.path("data").path("createCycle").path("cycleId").asInt();
    }

    private JsonNode correct(String token, int cycle, String count) {
        return graphql(token, "mutation { correctFingerlingsCount(cycleId: " + cycle
                + ", fingerlingsCount: " + count + ") { cycleId fingerlingsCount status } }");
    }

    /** Idadi iliyohifadhiwa, ikisomwa upya kutoka server. */
    private int stored() {
        JsonNode list = graphql(adminToken, "query { cycles { cycleId fingerlingsCount } }")
                .path("data").path("cycles");
        for (JsonNode cycle : list) {
            if (cycle.path("cycleId").asInt() == cycleId) {
                return cycle.path("fingerlingsCount").asInt();
            }
        }
        throw new AssertionError("Mzunguko " + cycleId + " haupo kwenye " + list);
    }

    @Test
    @DisplayName("mzunguko unaoendelea: idadi inarekebishwa na kuhifadhiwa")
    void correctsARunningCycle() {
        JsonNode res = correct(adminToken, cycleId, "120");

        assertThat(graphqlErrorCode(res)).as("kurekebisha: %s", res).isNull();
        JsonNode cycle = res.path("data").path("correctFingerlingsCount");
        assertThat(cycle.path("fingerlingsCount").asInt()).isEqualTo(120);
        assertThat(cycle.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(stored()).isEqualTo(120);
    }

    @Test
    @DisplayName("sifuri au hasi inakataliwa, na idadi haibadiliki")
    void refusesNonPositiveCount() {
        assertThat(graphqlErrorCode(correct(adminToken, cycleId, "0"))).isEqualTo("VALIDATION_ERROR");
        assertThat(graphqlErrorCode(correct(adminToken, cycleId, "-5"))).isEqualTo("VALIDATION_ERROR");
        assertThat(stored()).isEqualTo(100);
    }

    /** Kiwango halisi cha kuishi kimekwisha kokotolewa kutoka idadi hii. */
    @Test
    @DisplayName("mzunguko uliofungwa: kurekebisha kunakataliwa")
    void refusesAClosedCycle() {
        JsonNode sold = graphql(adminToken, "mutation { recordHarvestEvent(cycleId: " + cycleId
                + ", eventDate: \"2025-07-01\", fishCount: 120, weightKg: 60, reason: \"SOLD\""
                + ", saleAmount: 300000) { harvestEventId } }");
        assertThat(graphqlErrorCode(sold)).isNull();
        JsonNode closed = graphql(adminToken, "mutation { closeCycle(cycleId: " + cycleId
                + ", outcome: \"HARVESTED\", actualHarvestDate: \"2025-07-15\") { status } }");
        assertThat(graphqlErrorCode(closed)).isNull();

        assertThat(graphqlErrorCode(correct(adminToken, cycleId, "120")))
                .isEqualTo("CYCLE_ALREADY_CLOSED");
        assertThat(stored()).isEqualTo(100);
    }

    @Test
    @DisplayName("WORKER na VIEWER hawawezi kurekebisha (edit_cycle)")
    void workerAndViewerCannotCorrect() {
        assertThat(graphqlErrorCode(correct(workerToken, cycleId, "120"))).isEqualTo("FORBIDDEN");
        assertThat(graphqlErrorCode(correct(viewerToken, cycleId, "120"))).isEqualTo("FORBIDDEN");
        assertThat(stored()).isEqualTo(100);
    }

    @Test
    @DisplayName("mzunguko usiojulikana unakataliwa")
    void refusesUnknownCycle() {
        assertThat(graphqlErrorCode(correct(adminToken, 999_999, "120"))).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("mzunguko wa shamba jingine ni FORBIDDEN")
    void refusesAnotherFarmsCycle() {
        int foreign = inTx(() -> {
            Cycle cycle = new Cycle();
            cycle.setUnit(units.findByFarm_FarmId(farmB).get(0));
            cycle.setSpecies(species.findAll().get(0));
            cycle.setStockingDate(LocalDate.of(2025, 1, 10));
            cycle.setFingerlingsCount(500);
            cycle.setStatus(Cycle.ACTIVE);
            return cycles.save(cycle).getCycleId();
        });

        assertThat(graphqlErrorCode(correct(adminToken, foreign, "600"))).isEqualTo("FORBIDDEN");
    }
}
