package com.samaki.farm.dailytask;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.dailytask.entity.DailyTask;
import com.samaki.farm.dailytask.repository.DailyTaskRepository;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.species.repository.SpeciesRepository;
import com.samaki.farm.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI D - Kazi za kila siku (Task Completions).
 *
 * =====================================================================
 * KINACHOSHIKWA HAPA
 *
 * Module hii ndiyo INAYOSOMWA NA REMINDERS: swali la "kazi hii bado
 * inasubiri?" linalojibiwa hapa ndilo litakaloamua kama mtu anapigiwa
 * simu. Hivyo tests zake si za kuthibitisha mutation pekee - ni za
 * kuganda MKATABA:
 *
 *   done = kuna rekodi ya task_completions kwa (task_id, tarehe) yenye
 *          status DONE
 *
 * Rekodi ZINAHESABIWA KWA SQL HALISI, si kwa jibu la GraphQL pekee:
 * jibu linaweza kuwa sahihi huku hakuna kilichoandikwa (au kikaandikwa
 * mara mbili), na hilo ndilo hasa linalotakiwa kuzuiliwa kwenye jedwali
 * lenye UNIQUE.
 *
 * VIOLEZO vya shamba A vinatengenezwa kupitia `createCycle` HALISI, si
 * kwa kuandikwa hapa kwa mkono: FR-4.1 (CycleService.createDefaultTasks)
 * ndiyo njia pekee ambayo kazi zinazaliwa kwenye mfumo, na test
 * inayopanda violezo yenyewe ingekuwa inajaribu kitu ambacho hakipo
 * kwenye maisha halisi.
 *
 * Shamba B halina OWNER kwenye fixture, hivyo halina njia ya
 * `createCycle`; mzunguko wake na kiolezo chake vinaandikwa moja kwa
 * moja. Kinachohitajika kwake ni KUWEPO tu - ni shabaha ya D-1.
 * =====================================================================
 */
@DisplayName("D - Kazi za kila siku")
class DailyTaskCompletionRegressionTest extends IntegrationTest {

    @Autowired private DailyTaskRepository taskRepository;
    @Autowired private CycleRepository cycleRepository;
    @Autowired private ProductionUnitRepository unitRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private EntityManager entityManager;

    /** Mzunguko wa shamba A ulioundwa kupitia createCycle - una violezo vitatu. */
    private int cycleWithTasks;

    private int morningFeedTask;
    private int waterTask;
    private int eveningFeedTask;

    /** Shamba B - shabaha ya D-1. */
    private int cycleB;
    private int taskB;

    @BeforeEach
    void createTasks() {
        int speciesId = speciesRepository.findAll().get(0).getSpeciesId();

        JsonNode created = graphql(adminToken, "mutation { createCycle(input: {unitId: " + unitA
                + ", speciesId: " + speciesId + ", stockingDate: \"" + LocalDate.now()
                + "\", fingerlingsCount: 300}) { cycleId } }");
        cycleWithTasks = created.path("data").path("createCycle").path("cycleId").asInt();

        // Zimepangwa kwa saa: 07:00 kulisha asubuhi, 08:00 maji, 17:00
        // kulisha jioni (angalia CycleService.createDefaultTasks).
        List<DailyTask> tasks =
                taskRepository.findByCycle_CycleIdOrderByScheduledTimeAscTaskIdAsc(cycleWithTasks);
        morningFeedTask = tasks.get(0).getTaskId();
        waterTask = tasks.get(1).getTaskId();
        eveningFeedTask = tasks.get(2).getTaskId();

        cycleB = seedCycleOnFarmB();
        taskB = seedTask(cycleB, "Kulisha - Asubuhi", LocalTime.of(7, 0));
    }

    private int seedCycleOnFarmB() {
        Cycle cycle = new Cycle();
        cycle.setUnit(unitRepository.findById(unitB).orElseThrow());
        cycle.setSpecies(speciesRepository.findAll().get(0));
        cycle.setStockingDate(LocalDate.now().minusMonths(1));
        cycle.setFingerlingsCount(200);
        cycle.setStatus("ACTIVE");
        return cycleRepository.save(cycle).getCycleId();
    }

    private int seedTask(int cycleId, String type, LocalTime time) {
        DailyTask task = new DailyTask();
        task.setCycle(cycleRepository.findById(cycleId).orElseThrow());
        task.setTaskType(type);
        task.setScheduledTime(time);
        task.setFrequency("DAILY");
        return taskRepository.save(task).getTaskId();
    }

    // ------------------------------------------------------------ msaada

    private String completeMutation(int taskId, String extra) {
        return "mutation { completeTask(input: {taskId: " + taskId
                + (extra.isBlank() ? "" : ", " + extra)
                + "}) { taskId date status done completedByName notes } }";
    }

    private JsonNode dailyTasks(String token, int cycleId, String date) {
        String args = "cycleId: " + cycleId + (date == null ? "" : ", date: \"" + date + "\"");
        return graphql(token, "query { dailyTasks(" + args + ") "
                + "{ taskId taskType scheduledTime frequency assignedRoleName date status done "
                + "completedAt completedByName notes } }");
    }

    /** Hesabu ya rekodi KWENYE JEDWALI - si kwenye jibu la GraphQL. */
    private long completionRows() {
        return inTx(() -> ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM task_completions")
                .getSingleResult()).longValue());
    }

    private long completionRows(int taskId, String date) {
        return inTx(() -> ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM task_completions "
                        + "WHERE task_id = :task AND completion_date = CAST(:date AS DATE)")
                .setParameter("task", taskId)
                .setParameter("date", date)
                .getSingleResult()).longValue());
    }

    private String statusInDb(int taskId, String date) {
        return inTx(() -> (String) entityManager
                .createNativeQuery("SELECT status FROM task_completions "
                        + "WHERE task_id = :task AND completion_date = CAST(:date AS DATE)")
                .setParameter("task", taskId)
                .setParameter("date", date)
                .getSingleResult());
    }

    // ------------------------------------------------------------- tests

    @Nested
    @DisplayName("njia ya kuandika")
    class WritePath {

        @Test
        @DisplayName("WORKER anakamilisha kazi, na rekodi INAONEKANA kwenye jedwali")
        void workerCompletesAndRowIsWritten() {
            String today = LocalDate.now().toString();
            assertThat(completionRows()).isZero();

            JsonNode response = graphql(workerToken,
                    completeMutation(morningFeedTask, "notes: \"Nimelisha saa 1\""));
            JsonNode done = response.path("data").path("completeTask");

            assertThat(graphqlErrorCode(response)).isNull();
            assertThat(done.path("status").asText()).isEqualTo("DONE");
            assertThat(done.path("done").asBoolean()).isTrue();
            assertThat(done.path("date").asText()).isEqualTo(today);
            assertThat(done.path("completedByName").asText()).isEqualTo("Dev Worker");
            assertThat(done.path("notes").asText()).isEqualTo("Nimelisha saa 1");

            // Baada: rekodi MOJA, ikiwa DONE, kwa (kiolezo, leo).
            assertThat(completionRows()).isEqualTo(1);
            assertThat(completionRows(morningFeedTask, today)).isEqualTo(1);
            assertThat(statusInDb(morningFeedTask, today)).isEqualTo("DONE");
        }

        @Test
        @DisplayName("completionDate ikiachwa wazi inatumia leo")
        void defaultsToToday() {
            graphql(workerToken, completeMutation(waterTask, ""));

            assertThat(completionRows(waterTask, LocalDate.now().toString())).isEqualTo(1);
        }

        @Test
        @DisplayName("kiolezo kile kile, tarehe TOFAUTI, ni rekodi nyingine - si kugongana")
        void sameTaskOnAnotherDayIsASeparateRow() {
            // Ndiyo maana ya UNIQUE(task_id, completion_date): kiolezo
            // kinajirudia kila siku, hivyo kukamilisha cha jana na cha leo
            // ni vitendo viwili halali.
            assertThat(graphqlErrorCode(graphql(workerToken,
                    completeMutation(morningFeedTask, "completionDate: \"2026-08-30\"")))).isNull();
            assertThat(graphqlErrorCode(graphql(workerToken,
                    completeMutation(morningFeedTask, "completionDate: \"2026-08-31\"")))).isNull();

            assertThat(completionRows()).isEqualTo(2);
        }

        @Test
        @DisplayName("violezo vitatu vya createCycle vinakamilishwa kila kimoja peke yake")
        void eachTemplateIsCompletedIndependently() {
            graphql(workerToken, completeMutation(morningFeedTask, ""));
            graphql(workerToken, completeMutation(eveningFeedTask, ""));

            JsonNode listed = dailyTasks(workerToken, cycleWithTasks, null)
                    .path("data").path("dailyTasks");
            assertThat(listed).hasSize(3);
            assertThat(listed.get(0).path("done").asBoolean()).isTrue();   // 07:00
            assertThat(listed.get(1).path("done").asBoolean()).isFalse();  // 08:00 maji
            assertThat(listed.get(2).path("done").asBoolean()).isTrue();   // 17:00
        }
    }

    @Nested
    @DisplayName("kugongana: kukamilisha mara mbili")
    class DoubleComplete {

        @Test
        @DisplayName("kukamilisha kiolezo kile kile siku ile ile ni CONFLICT")
        void secondCompletionIsRejected() {
            assertThat(graphqlErrorCode(graphql(workerToken, completeMutation(morningFeedTask, ""))))
                    .isNull();

            JsonNode second = graphql(workerToken, completeMutation(morningFeedTask, ""));

            assertThat(graphqlErrorCode(second)).isEqualTo("CONFLICT");
            assertThat(graphqlMessage(second)).contains("tayari imewekwa kuwa imekamilika");
        }

        @Test
        @DisplayName("baada ya kukataliwa, rekodi bado ni MOJA")
        void rejectedAttemptWritesNothing() {
            String today = LocalDate.now().toString();
            graphql(workerToken, completeMutation(morningFeedTask, "notes: \"ya kwanza\""));
            graphql(workerToken, completeMutation(morningFeedTask, "notes: \"ya pili\""));

            assertThat(completionRows(morningFeedTask, today)).isEqualTo(1);
            // Ushahidi wa wa kwanza haujafutwa na jaribio la pili.
            assertThat(dailyTasks(workerToken, cycleWithTasks, today).path("data").path("dailyTasks")
                    .get(0).path("notes").asText()).isEqualTo("ya kwanza");
        }

        @Test
        @DisplayName("mtu MWINGINE naye anakataliwa - si kigongano cha mtumiaji mmoja")
        void anotherUserIsRejectedToo() {
            graphql(workerToken, completeMutation(morningFeedTask, ""));

            // OWNER naye ana mark_task_done, na kazi ni ya shamba lake.
            assertThat(graphqlErrorCode(graphql(adminToken, completeMutation(morningFeedTask, ""))))
                    .isEqualTo("CONFLICT");
        }

        @Test
        @DisplayName("UNIQUE(task_id, completion_date) ndiyo kikwazo cha mwisho - kipo kwenye schema")
        void theUniqueConstraintExists() {
            // Ukaguzi wa huduma ni wa ujumbe mzuri; hiki ndicho
            // kinachozuia maombi mawili yanayowasili kwa wakati mmoja.
            Object found = inTx(() -> entityManager.createNativeQuery(
                            "SELECT conname FROM pg_constraint "
                                    + "WHERE conrelid = CAST('task_completions' AS regclass) "
                                    + "AND contype = 'u'")
                    .getSingleResult());

            assertThat(found.toString()).isEqualTo("task_completions_task_id_completion_date_key");
        }
    }

    @Nested
    @DisplayName("lango la ruhusa")
    class Permissions {

        @Test
        @DisplayName("VIEWER hawezi kukamilisha - hana mark_task_done")
        void viewerCannotComplete() {
            JsonNode response = graphql(viewerToken, completeMutation(morningFeedTask, ""));

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("mark_task_done");
            assertThat(completionRows()).isZero();
        }

        @Test
        @DisplayName("VIEWER anaweza kusoma - ana view_dashboard")
        void viewerCanRead() {
            JsonNode response = dailyTasks(viewerToken, cycleWithTasks, null);

            assertThat(graphqlErrorCode(response)).isNull();
            assertThat(response.path("data").path("dailyTasks")).hasSize(3);
        }

        @Test
        @DisplayName("asiye na role hakatai kusoma wala kuandika - lango ni ruhusa, si uanachama")
        void noRoleIsBlockedBothWays() {
            JsonNode read = dailyTasks(noroleToken, cycleWithTasks, null);
            assertThat(graphqlErrorCode(read)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(read)).contains("view_dashboard");

            JsonNode write = graphql(noroleToken, completeMutation(morningFeedTask, ""));
            assertThat(graphqlErrorCode(write)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(write)).contains("mark_task_done");
        }

        @Test
        @DisplayName("WORKER anafaulu ombi lile lile ambalo VIEWER amekataliwa")
        void workerSucceedsWhereViewerFailed() {
            assertThat(graphqlErrorCode(graphql(viewerToken, completeMutation(morningFeedTask, ""))))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(workerToken, completeMutation(morningFeedTask, ""))))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("D-1: kuvuja kati ya mashamba")
    class CrossTenant {

        @Test
        @DisplayName("mfanyakazi wa shamba B hawezi kukamilisha kazi ya shamba A")
        void bCannotCompleteAsTask() {
            JsonNode response = graphql(workerBToken, completeMutation(morningFeedTask, ""));

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).isEqualTo("Huruhusiwi kufikia shamba hili.");
            assertThat(completionRows()).isZero();
        }

        @Test
        @DisplayName("upande wa pili pia: shamba A kwenda B ni FORBIDDEN")
        void theOtherDirectionIsBlockedToo() {
            // Pande MBILI kwa makusudi: ukaguzi ulioandikwa upande mmoja
            // pekee ndio hasa ulikuwa umeacha D-1 wazi kwenye CycleService.
            assertThat(graphqlErrorCode(graphql(workerToken, completeMutation(taskB, ""))))
                    .isEqualTo("FORBIDDEN");
            assertThat(completionRows()).isZero();
        }

        @Test
        @DisplayName("kusoma kazi za mzunguko wa shamba jingine ni FORBIDDEN, pande zote mbili")
        void readingAnotherFarmsCycleIsForbidden() {
            assertThat(graphqlErrorCode(dailyTasks(workerBToken, cycleWithTasks, null)))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(dailyTasks(workerToken, cycleB, null)))
                    .isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("kila shamba linaona kazi ZAKE pekee")
        void eachFarmSeesOnlyItsOwn() {
            assertThat(dailyTasks(workerToken, cycleWithTasks, null).path("data").path("dailyTasks"))
                    .hasSize(3);
            assertThat(dailyTasks(workerBToken, cycleB, null).path("data").path("dailyTasks"))
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("mkataba wa Reminders: outstanding dhidi ya DONE")
    class OutstandingContract {

        @Test
        @DisplayName("kabla ya kukamilisha, kazi zote ni OUTSTANDING na done=false")
        void everythingStartsOutstanding() {
            JsonNode listed = dailyTasks(workerToken, cycleWithTasks, null)
                    .path("data").path("dailyTasks");

            assertThat(listed).hasSize(3);
            listed.forEach(task -> {
                assertThat(task.path("status").asText()).isEqualTo("OUTSTANDING");
                assertThat(task.path("done").asBoolean()).isFalse();
                assertThat(task.path("completedAt").isNull()).isTrue();
                assertThat(task.path("completedByName").isNull()).isTrue();
            });
        }

        @Test
        @DisplayName("baada ya kukamilisha, ILE ILE inakuwa DONE na nyingine zinabaki")
        void completionFlipsExactlyOne() {
            graphql(workerToken, completeMutation(waterTask, ""));

            JsonNode listed = dailyTasks(workerToken, cycleWithTasks, null)
                    .path("data").path("dailyTasks");

            JsonNode water = listed.get(1);
            assertThat(water.path("taskId").asInt()).isEqualTo(waterTask);
            assertThat(water.path("status").asText()).isEqualTo("DONE");
            assertThat(water.path("done").asBoolean()).isTrue();
            assertThat(water.path("completedByName").asText()).isEqualTo("Dev Worker");
            assertThat(water.path("completedAt").asText()).isNotBlank();

            // Zilizobaki hazijaguswa - ndicho Reminders itakachokumbusha.
            assertThat(listed.get(0).path("done").asBoolean()).isFalse();
            assertThat(listed.get(2).path("done").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("ukamilishaji wa SIKU NYINGINE hauifanyi kazi ya leo kuwa DONE")
        void completionIsPerDayNotPerTemplate() {
            // Hii ndiyo hoja nzima ya kutokuwa bendera juu ya daily_tasks:
            // kiolezo kilichokamilishwa jana bado kinasubiri leo.
            graphql(workerToken, completeMutation(morningFeedTask, "completionDate: \"2026-08-30\""));

            JsonNode yesterday = dailyTasks(workerToken, cycleWithTasks, "2026-08-30")
                    .path("data").path("dailyTasks");
            assertThat(yesterday.get(0).path("done").asBoolean()).isTrue();

            JsonNode today = dailyTasks(workerToken, cycleWithTasks, null)
                    .path("data").path("dailyTasks");
            assertThat(today.get(0).path("done").asBoolean()).isFalse();
            assertThat(today.get(0).path("status").asText()).isEqualTo("OUTSTANDING");
        }

        @Test
        @DisplayName("rekodi ya MISSED inabaki outstanding - kazi isiyofanyika ni kazi isiyofanyika")
        void missedRowIsStillOutstanding() {
            // MISSED/PENDING/LATE ziko kwenye orodha ya V1 lakini hakuna
            // kinachoziandika bado; inaandikwa hapa kwa SQL ili sheria
            // "done = DONE PEKEE" ishikwe kabla mtu hajaifungua.
            String today = LocalDate.now().toString();
            inTx(() -> entityManager.createNativeQuery(
                            "INSERT INTO task_completions (task_id, completion_date, status) "
                                    + "VALUES (:task, CAST(:date AS DATE), 'MISSED')")
                    .setParameter("task", morningFeedTask)
                    .setParameter("date", today)
                    .executeUpdate());

            JsonNode task = dailyTasks(workerToken, cycleWithTasks, today)
                    .path("data").path("dailyTasks").get(0);

            assertThat(task.path("status").asText()).isEqualTo("MISSED");
            assertThat(task.path("done").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("rekodi ya MISSED inaweza kugeuzwa DONE - UNIQUE inaruhusu moja tu")
        void missedRowCanStillBeCompleted() {
            String today = LocalDate.now().toString();
            inTx(() -> entityManager.createNativeQuery(
                            "INSERT INTO task_completions (task_id, completion_date, status) "
                                    + "VALUES (:task, CAST(:date AS DATE), 'MISSED')")
                    .setParameter("task", morningFeedTask)
                    .setParameter("date", today)
                    .executeUpdate());

            JsonNode response = graphql(workerToken, completeMutation(morningFeedTask, ""));

            assertThat(graphqlErrorCode(response)).isNull();
            assertThat(statusInDb(morningFeedTask, today)).isEqualTo("DONE");
            // Bado rekodi MOJA - imegeuzwa, haijaongezwa.
            assertThat(completionRows(morningFeedTask, today)).isEqualTo(1);
        }

        @Test
        @DisplayName("scheduledTime inarudishwa ili Reminders ikokotoe 'overdue' yenyewe")
        void scheduledTimeIsExposed() {
            JsonNode listed = dailyTasks(workerToken, cycleWithTasks, null)
                    .path("data").path("dailyTasks");

            assertThat(listed.get(0).path("scheduledTime").asText()).isEqualTo("07:00");
            assertThat(listed.get(1).path("scheduledTime").asText()).isEqualTo("08:00");
            assertThat(listed.get(2).path("scheduledTime").asText()).isEqualTo("17:00");
        }

        @Test
        @DisplayName("assignedRoleName ni null kwa kazi zote - Reminders inahitaji uamuzi")
        void assignedRoleIsNullOnGeneratedTasks() {
            // BENDERA, si hitilafu inayorekebishwa hapa: CycleService
            // haiweki assigned_role_id, hivyo Reminders haina role ya
            // kutafutia wapokeaji (angalia ripoti ya batch hii).
            JsonNode listed = dailyTasks(workerToken, cycleWithTasks, null)
                    .path("data").path("dailyTasks");

            listed.forEach(task -> assertThat(task.path("assignedRoleName").isNull()).isTrue());
        }

        @Test
        @DisplayName("mzunguko usio na violezo unarudisha orodha tupu, si hitilafu")
        void cycleWithoutTemplatesIsEmpty() {
            // cycleA ni wa fixture (umeandikwa moja kwa moja, bila
            // createDefaultTasks) - hivyo hauna kazi hata moja.
            JsonNode response = dailyTasks(workerToken, cycleA, null);

            assertThat(graphqlErrorCode(response)).isNull();
            assertThat(response.path("data").path("dailyTasks")).isEmpty();
        }
    }

    @Nested
    @DisplayName("kinachokataliwa")
    class Rejects {

        @Test
        @DisplayName("kazi isiyojulikana")
        void unknownTask() {
            JsonNode response = graphql(workerToken, completeMutation(999999, ""));

            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(response)).isEqualTo("Kazi haijulikani");
        }

        @Test
        @DisplayName("mzunguko usiojulikana kwenye query")
        void unknownCycle() {
            JsonNode response = dailyTasks(workerToken, 999999, null);

            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(response)).isEqualTo("Mzunguko haujulikani");
        }

        @Test
        @DisplayName("tarehe isiyosomeka")
        void unparseableDate() {
            JsonNode response = graphql(workerToken,
                    completeMutation(morningFeedTask, "completionDate: \"30-08-2026\""));

            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(response)).contains("YYYY-MM-DD");
        }

        @Test
        @DisplayName("hakuna kilichoandikwa baada ya kukataliwa kokote")
        void nothingIsPersistedOnReject() {
            graphql(workerToken, completeMutation(999999, ""));
            graphql(workerToken, completeMutation(morningFeedTask, "completionDate: \"30-08-2026\""));
            graphql(viewerToken, completeMutation(morningFeedTask, ""));
            graphql(workerBToken, completeMutation(morningFeedTask, ""));

            assertThat(completionRows()).isZero();
        }
    }
}
