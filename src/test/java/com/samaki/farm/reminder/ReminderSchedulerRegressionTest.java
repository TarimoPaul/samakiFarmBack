package com.samaki.farm.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.common.notification.PushSender;
import com.samaki.farm.common.notification.SmsSender;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.dailytask.entity.DailyTask;
import com.samaki.farm.dailytask.repository.DailyTaskRepository;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.reminder.config.ReminderProperties;
import com.samaki.farm.reminder.entity.Reminder;
import com.samaki.farm.reminder.services.OutstandingTaskSelector;
import com.samaki.farm.reminder.services.ReminderDispatchService;
import com.samaki.farm.reminder.services.ReminderRecipientService;
import com.samaki.farm.species.repository.SpeciesRepository;
import com.samaki.farm.support.IntegrationTest;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * KUNDI E - Vikumbusho (Reminders scheduler).
 *
 * =====================================================================
 * HAKUNA SMS WALA PUSH INAYOTOKA HAPA
 *
 * `SmsSender` na `PushSender` ni @MockBean - bean halisi (stub za logs)
 * zimeondolewa kwenye context ya majaribio haya. Kila madai ni juu ya
 * KILICHOITWA: "smsSender.send iliitwa mara ngapi, kwa namba gani".
 * Hilo ndilo hasa jambo ambalo tests za vikumbusho zinaweza kulikosea
 * kwa gharama kubwa - test inayotuma kweli ingempigia mtu halisi kila
 * `mvn test`.
 *
 * TIKI YA CRON IMEZIMWA kwenye profile ya majaribio
 * (`app.reminders.enabled=false`, angalia application-test.yml), hivyo
 * kila tiki hapa ni ya mkono, kwa tarehe na saa zinazojulikana. Bila
 * hilo, cron ingeweza kuamka katikati ya test na kuandika rekodi
 * ambazo test haikuziomba.
 *
 * REKODI ZINAHESABIWA KWA SQL HALISI, si kwa jibu la huduma - hoja ile
 * ile ya Kundi D: jibu linaweza kuonekana sahihi huku jedwali likiwa na
 * rekodi mbili, na jedwali lenye UNIQUE ndilo hasa linalotakiwa
 * kushikwa.
 * =====================================================================
 *
 * FIXTURE: shamba A lina wenye `mark_task_done` WAWILI (Dev Admin =
 * OWNER, Dev Worker = WORKER) na wasio nao wawili (VIEWER, asiye na
 * role). Ndiyo maana idadi za msingi hapa ni:
 *
 *   kazi 3 x wapokeaji 2 = SMS 6, pamoja na PUSH 3 (mfanyakazi pekee
 *   ndiye amepewa push_token) = rekodi 9 kwa siku.
 */
@DisplayName("E - Vikumbusho")
class ReminderSchedulerRegressionTest extends IntegrationTest {

    /**
     * Saa mbili usiku (20:00) - baada ya kiolezo cha mwisho (17:00), hivyo
     * kazi ZOTE za siku zimeiva, LAKINI kabla ya saa za kimya (21:00).
     *
     * Si 23:59 kwa makusudi: saa hiyo iko NDANI ya dirisha la kimya, hivyo
     * `runTickAt` ingerudi bila kutuma chochote - na test ingeonekana kama
     * uchaguzi umeharibika ilhali ulinzi wa saa za kimya ndio uliofanya
     * kazi yake.
     */
    private static final LocalTime AFTER_ALL_TASKS = LocalTime.of(20, 0);

    private static final String WORKER_PUSH_TOKEN = "dev-push-token-worker";

    @Autowired private OutstandingTaskSelector selector;
    @Autowired private ReminderRecipientService recipients;
    @Autowired private ReminderDispatchService dispatch;
    @Autowired private ReminderProperties properties;
    @Autowired private DailyTaskRepository taskRepository;
    @Autowired private CycleRepository cycleRepository;
    @Autowired private ProductionUnitRepository unitRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private EntityManager entityManager;

    @MockBean private SmsSender smsSender;
    @MockBean private PushSender pushSender;

    private LocalDate today;

    /** Mzunguko wa shamba A ulioundwa kupitia createCycle - una violezo vitatu. */
    private int cycleWithTasks;
    private int morningFeedTask;   // 07:00
    private int waterTask;         // 08:00
    private int eveningFeedTask;   // 17:00

    /** Shamba B - shabaha ya kuvuja kati ya mashamba. */
    private int cycleB;
    private int taskB;

    @BeforeEach
    void createTasksAndTokens() {
        today = LocalDate.now();
        int speciesId = speciesRepository.findAll().get(0).getSpeciesId();

        // Violezo vinazaliwa kwa njia HALISI (createCycle -> createDefaultTasks),
        // si kwa kuandikwa hapa - hoja ile ile ya Kundi D.
        JsonNode created = graphql(adminToken, "mutation { createCycle(input: {unitId: " + unitA
                + ", speciesId: " + speciesId + ", stockingDate: \"" + today
                + "\", fingerlingsCount: 300}) { cycleId } }");
        cycleWithTasks = created.path("data").path("createCycle").path("cycleId").asInt();

        List<DailyTask> tasks =
                taskRepository.findByCycle_CycleIdOrderByScheduledTimeAscTaskIdAsc(cycleWithTasks);
        morningFeedTask = tasks.get(0).getTaskId();
        waterTask = tasks.get(1).getTaskId();
        eveningFeedTask = tasks.get(2).getTaskId();

        cycleB = seedCycleOnFarmB();
        taskB = seedTask(cycleB, "Kulisha - Asubuhi", LocalTime.of(7, 0));

        // MFANYAKAZI PEKEE ndiye ana push_token. Ndiyo inayofanya "njia
        // mbili" ijaribike: admin ana simu tu, mfanyakazi ana vyote.
        givePushToken(WORKER_PHONE, WORKER_PUSH_TOKEN);
    }

    // ------------------------------------------------------------ msaada

    private int seedCycleOnFarmB() {
        Cycle cycle = new Cycle();
        cycle.setUnit(unitRepository.findById(unitB).orElseThrow());
        cycle.setSpecies(speciesRepository.findAll().get(0));
        cycle.setStockingDate(today.minusMonths(1));
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

    private void givePushToken(String phone, String token) {
        User user = userRepository.findByPhone(phone).orElseThrow();
        user.setPushToken(token);
        userRepository.save(user);
    }

    private void completeTask(int taskId) {
        assertThat(graphqlErrorCode(graphql(workerToken,
                "mutation { completeTask(input: {taskId: " + taskId + "}) { done } }"))).isNull();
    }

    private ReminderDispatchService.TickResult tickFarmA() {
        return dispatch.runForFarm(farmA, today, AFTER_ALL_TASKS);
    }

    private List<Integer> taskIdsOf(List<DailyTask> tasks) {
        return tasks.stream().map(DailyTask::getTaskId).toList();
    }

    /** Hesabu ya rekodi KWENYE JEDWALI - si kwenye jibu la huduma. */
    private long reminderRows() {
        return inTx(() -> ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM reminders")
                .getSingleResult()).longValue());
    }

    private long reminderRows(int taskId, UUID recipientId, String channel) {
        return inTx(() -> ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM reminders WHERE task_id = :task "
                        + "AND reminder_date = CAST(:date AS DATE) "
                        + "AND recipient_user_id = CAST(:user AS UUID) AND channel = :channel")
                .setParameter("task", taskId)
                .setParameter("date", today.toString())
                .setParameter("user", recipientId.toString())
                .setParameter("channel", channel)
                .getSingleResult()).longValue());
    }

    private String reminderStatus(int taskId, UUID recipientId, String channel) {
        return inTx(() -> (String) entityManager
                .createNativeQuery("SELECT status FROM reminders WHERE task_id = :task "
                        + "AND reminder_date = CAST(:date AS DATE) "
                        + "AND recipient_user_id = CAST(:user AS UUID) AND channel = :channel")
                .setParameter("task", taskId)
                .setParameter("date", today.toString())
                .setParameter("user", recipientId.toString())
                .setParameter("channel", channel)
                .getSingleResult());
    }

    private boolean sentAtIsNull(int taskId, UUID recipientId, String channel) {
        return inTx(() -> (Boolean) entityManager
                .createNativeQuery("SELECT sent_at IS NULL FROM reminders WHERE task_id = :task "
                        + "AND reminder_date = CAST(:date AS DATE) "
                        + "AND recipient_user_id = CAST(:user AS UUID) AND channel = :channel")
                .setParameter("task", taskId)
                .setParameter("date", today.toString())
                .setParameter("user", recipientId.toString())
                .setParameter("channel", channel)
                .getSingleResult());
    }

    // ------------------------------------------------------------- tests

    @Nested
    @DisplayName("uchaguzi: zilizobaki dhidi ya zilizofanyika")
    class Selection {

        @Test
        @DisplayName("kabla ya kukamilisha, kazi zote tatu za shamba zinasubiri")
        void everythingStartsOutstanding() {
            List<DailyTask> outstanding = selector.outstanding(farmA, today);

            assertThat(taskIdsOf(outstanding))
                    .containsExactly(morningFeedTask, waterTask, eveningFeedTask);
        }

        @Test
        @DisplayName("iliyowekwa DONE HAIRUDI - ni sheria ile ile ya Task Completions")
        void doneTaskIsNeverSelected() {
            completeTask(waterTask);

            List<DailyTask> outstanding = selector.outstanding(farmA, today);

            assertThat(taskIdsOf(outstanding)).containsExactly(morningFeedTask, eveningFeedTask);
            assertThat(taskIdsOf(outstanding)).doesNotContain(waterTask);
        }

        @Test
        @DisplayName("rekodi ya MISSED bado inasubiri - kazi isiyofanyika ni kazi isiyofanyika")
        void missedRowIsStillOutstanding() {
            inTx(() -> entityManager.createNativeQuery(
                            "INSERT INTO task_completions (task_id, completion_date, status) "
                                    + "VALUES (:task, CAST(:date AS DATE), 'MISSED')")
                    .setParameter("task", morningFeedTask)
                    .setParameter("date", today.toString())
                    .executeUpdate());

            assertThat(taskIdsOf(selector.outstanding(farmA, today))).contains(morningFeedTask);
        }

        @Test
        @DisplayName("ukamilishaji wa SIKU NYINGINE hauondoi kazi ya leo")
        void completionIsPerDay() {
            String yesterday = today.minusDays(1).toString();
            assertThat(graphqlErrorCode(graphql(workerToken, "mutation { completeTask(input: {taskId: "
                    + morningFeedTask + ", completionDate: \"" + yesterday + "\"}) { done } }"))).isNull();

            assertThat(taskIdsOf(selector.outstanding(farmA, today))).contains(morningFeedTask);
            assertThat(taskIdsOf(selector.outstanding(farmA, today.minusDays(1))))
                    .doesNotContain(morningFeedTask);
        }

        @Test
        @DisplayName("saa ya kazi ikiwa BADO HAIJAFIKA, haikumbushwi")
        void notYetDueIsNotSelected() {
            // Saa 07:30: kulisha kwa 07:00 kumeiva; maji (08:00) na kulisha
            // jioni (17:00) bado. Kikumbusho kinachotangulia saa ya kazi ni
            // kero, si kikumbusho.
            List<DailyTask> due = selector.dueForReminder(farmA, today, LocalTime.of(7, 30));

            assertThat(taskIdsOf(due)).containsExactly(morningFeedTask);
        }

        @Test
        @DisplayName("mzunguko usio ACTIVE hautoi vikumbusho tena")
        void harvestedCycleIsExcluded() {
            // Kiolezo hakina tarehe ya mwisho: bila kichujio hiki, mzunguko
            // uliovunwa ungeendelea kukumbusha milele.
            Cycle cycle = cycleRepository.findById(cycleWithTasks).orElseThrow();
            cycle.setStatus("HARVESTED");
            cycleRepository.save(cycle);

            assertThat(selector.outstanding(farmA, today)).isEmpty();
        }
    }

    @Nested
    @DisplayName("wapokeaji: wenye mark_task_done pekee")
    class Recipients {

        @Test
        @DisplayName("shamba A - OWNER na WORKER, si VIEWER wala asiye na role")
        void onlyHoldersOfThePermission() {
            List<String> names = recipients.forFarm(farmA).stream().map(User::getName).toList();

            assertThat(names).containsExactly("Dev Admin", "Dev Worker");
            assertThat(names).doesNotContain("Dev Viewer", "Dev Norole");
        }

        @Test
        @DisplayName("lango ni RUHUSA, si role: ikiondolewa kwa WORKER, anatoka kwenye orodha")
        void removingThePermissionRemovesTheRecipient() {
            // Si jina la role linaloamua - ndiyo hoja nzima. Role ile ile,
            // ikiwa haina `mark_task_done`, haipati kikumbusho.
            inTx(() -> entityManager.createNativeQuery(
                            "DELETE FROM role_permissions WHERE role_id = "
                                    + "(SELECT role_id FROM roles WHERE name = 'WORKER') "
                                    + "AND permission_id = "
                                    + "(SELECT permission_id FROM permissions WHERE code = 'mark_task_done')")
                    .executeUpdate());

            assertThat(recipients.forFarm(farmA).stream().map(User::getName).toList())
                    .containsExactly("Dev Admin");
        }

        @Test
        @DisplayName("aliyezuiwa (DISABLED) hakumbushwi - hawezi kuingia kuifunga kazi")
        void disabledUserIsExcluded() {
            User worker = userRepository.findByPhone(WORKER_PHONE).orElseThrow();
            worker.setStatus(UserStatus.DISABLED);
            userRepository.save(worker);

            assertThat(recipients.forFarm(farmA).stream().map(User::getName).toList())
                    .containsExactly("Dev Admin");
        }

        @Test
        @DisplayName("kila shamba lina wapokeaji WAKE")
        void recipientsAreScopedToTheFarm() {
            assertThat(recipients.forFarm(farmB).stream().map(User::getName).toList())
                    .containsExactly("Dev Worker B");
        }
    }

    @Nested
    @DisplayName("njia mbili, si mbadala")
    class BothChannels {

        @Test
        @DisplayName("mwenye simu NA push_token anapata ZOTE MBILI")
        void recipientWithBothGetsBoth() {
            tickFarmA();

            // Mfanyakazi: SMS 3 (kazi tatu) NA push 3 - si moja badala ya
            // nyingine.
            verify(smsSender, times(3)).send(eq(WORKER_PHONE), anyString());
            verify(pushSender, times(3)).send(eq(WORKER_PUSH_TOKEN), anyString(), anyString());

            assertThat(reminderRows(morningFeedTask, workerId, Reminder.SMS)).isEqualTo(1);
            assertThat(reminderRows(morningFeedTask, workerId, Reminder.PUSH)).isEqualTo(1);
        }

        @Test
        @DisplayName("asiye na push_token anapata SMS pekee - hakuna push ya bure")
        void recipientWithoutTokenGetsSmsOnly() {
            tickFarmA();

            verify(smsSender, times(3)).send(eq(ADMIN_PHONE), anyString());
            // Push ZOTE ni za mfanyakazi; admin hana token, hivyo hapati.
            verify(pushSender, times(3)).send(eq(WORKER_PUSH_TOKEN), anyString(), anyString());
            verify(pushSender, times(3)).send(anyString(), anyString(), anyString());

            assertThat(reminderRows(morningFeedTask, adminId, Reminder.PUSH)).isZero();
        }

        @Test
        @DisplayName("jumla ya tiki moja: SMS 6, PUSH 3, rekodi 9")
        void oneTickInFull() {
            ReminderDispatchService.TickResult result = tickFarmA();

            assertThat(result.tasks()).isEqualTo(3);
            assertThat(result.sent()).isEqualTo(9);
            assertThat(result.failed()).isZero();
            assertThat(result.skipped()).isZero();

            verify(smsSender, times(6)).send(anyString(), anyString());
            verify(pushSender, times(3)).send(anyString(), anyString(), anyString());
            assertThat(reminderRows()).isEqualTo(9);
        }

        @Test
        @DisplayName("ujumbe unataja kazi, saa yake, na tarehe")
        void messageNamesTheTask() {
            dispatch.runForFarm(farmA, today, LocalTime.of(7, 30));

            verify(smsSender).send(eq(WORKER_PHONE), contains("Kulisha"));
            verify(smsSender).send(eq(WORKER_PHONE), contains("07:00"));
            verify(smsSender).send(eq(WORKER_PHONE), contains(today.toString()));
        }

        @Test
        @DisplayName("kazi iliyofungwa haimletei mtu yeyote kikumbusho")
        void doneTaskRemindsNobody() {
            completeTask(morningFeedTask);

            ReminderDispatchService.TickResult result = tickFarmA();

            assertThat(result.tasks()).isEqualTo(2);
            assertThat(result.sent()).isEqualTo(6);
            assertThat(reminderRows(morningFeedTask, workerId, Reminder.SMS)).isZero();
        }
    }

    @Nested
    @DisplayName("kutotuma mara mbili")
    class Idempotency {

        @Test
        @DisplayName("tiki mbili -> kila (kazi, mtu, njia) imetumwa MARA MOJA")
        void secondTickSendsNothing() {
            tickFarmA();
            ReminderDispatchService.TickResult second = tickFarmA();

            // MARA MOJA, si mara mbili - hata baada ya tiki ya pili.
            verify(smsSender, times(6)).send(anyString(), anyString());
            verify(pushSender, times(3)).send(anyString(), anyString(), anyString());

            assertThat(second.sent()).isZero();
            assertThat(second.skipped()).isEqualTo(9);
            assertThat(reminderRows()).isEqualTo(9);
        }

        @Test
        @DisplayName("hata tiki ya tatu na ya nne hazizidishi chochote")
        void repeatedTicksStayFlat() {
            tickFarmA();
            tickFarmA();
            tickFarmA();
            tickFarmA();

            verify(smsSender, times(6)).send(anyString(), anyString());
            assertThat(reminderRows()).isEqualTo(9);
        }

        @Test
        @DisplayName("SIKU NYINGINE ni kikumbusho kingine - logi haizuii kesho")
        void anotherDayIsAnotherReminder() {
            tickFarmA();
            dispatch.runForFarm(farmA, today.plusDays(1), AFTER_ALL_TASKS);

            // Kiolezo kinajirudia kila siku, ndiyo maana reminder_date iko
            // kwenye UNIQUE. Bila yake, kikumbusho cha leo kingezuia cha kesho.
            verify(smsSender, times(12)).send(anyString(), anyString());
            assertThat(reminderRows()).isEqualTo(18);
        }

        @Test
        @DisplayName("UNIQUE(task, tarehe, mpokeaji, njia) ndiyo kikwazo cha mwisho - kipo kwenye schema")
        void theUniqueConstraintExists() {
            // Ukaguzi wa Java ungeweza kupitwa na tiki mbili za wakati mmoja;
            // hiki hakiwezi.
            Object found = inTx(() -> entityManager.createNativeQuery(
                            "SELECT conname FROM pg_constraint "
                                    + "WHERE conrelid = CAST('reminders' AS regclass) "
                                    + "AND contype = 'u'")
                    .getSingleResult());

            assertThat(found.toString()).isEqualTo("reminders_task_date_recipient_channel_key");
        }
    }

    @Nested
    @DisplayName("kuvuja kati ya mashamba")
    class CrossTenant {

        @Test
        @DisplayName("tiki ya shamba B haigusi kazi wala watu wa shamba A")
        void farmBTickExcludesFarmA() {
            ReminderDispatchService.TickResult result = dispatch.runForFarm(farmB, today, AFTER_ALL_TASKS);

            assertThat(result.tasks()).isEqualTo(1);
            assertThat(result.sent()).isEqualTo(1);

            verify(smsSender, times(1)).send(eq(WORKER_B_PHONE), anyString());
            verify(smsSender, never()).send(eq(ADMIN_PHONE), anyString());
            verify(smsSender, never()).send(eq(WORKER_PHONE), anyString());
            verify(pushSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("upande wa pili pia: tiki ya shamba A haigusi kazi ya shamba B")
        void farmATickExcludesFarmB() {
            // Pande MBILI kwa makusudi - ukaguzi wa upande mmoja ndio hasa
            // ulioacha D-1 wazi kwenye CycleService.
            tickFarmA();

            verify(smsSender, never()).send(eq(WORKER_B_PHONE), anyString());
            assertThat(reminderRows()).isEqualTo(9);
            long rowsForB = inTx(() -> ((Number) entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM reminders WHERE task_id = :task")
                    .setParameter("task", taskB)
                    .getSingleResult()).longValue());
            assertThat(rowsForB).isZero();
        }

        @Test
        @DisplayName("tiki ya mashamba yote inashughulikia kila shamba kivyake")
        void fullTickCoversEveryFarmSeparately() {
            ReminderDispatchService.TickResult result = dispatch.runTickAt(today, AFTER_ALL_TASKS);

            assertThat(result.farms()).isEqualTo(2);
            assertThat(result.sent()).isEqualTo(10);   // 9 za shamba A + 1 ya shamba B
            verify(smsSender, times(1)).send(eq(WORKER_B_PHONE), anyString());
            verify(smsSender, times(3)).send(eq(WORKER_PHONE), anyString());
        }
    }

    @Nested
    @DisplayName("ustahimilivu: mmoja akianguka, wengine wanaendelea")
    class Resilience {

        @Test
        @DisplayName("SMS ya mtu mmoja ikilipuka, mwenzake bado anapata zake")
        void oneFailureDoesNotStopTheOthers() {
            doThrow(new RuntimeException("provider hayupo"))
                    .when(smsSender).send(eq(ADMIN_PHONE), anyString());

            ReminderDispatchService.TickResult result = tickFarmA();

            // Zilizoshindwa: SMS 3 za admin. Zilizofaulu: SMS 3 + push 3 za
            // mfanyakazi.
            assertThat(result.failed()).isEqualTo(3);
            assertThat(result.sent()).isEqualTo(6);
            verify(smsSender, times(3)).send(eq(WORKER_PHONE), anyString());
            verify(pushSender, times(3)).send(eq(WORKER_PUSH_TOKEN), anyString(), anyString());
        }

        @Test
        @DisplayName("iliyoshindwa inaandikwa FAILED, na sent_at inabaki tupu")
        void failureIsRecorded() {
            doThrow(new RuntimeException("provider hayupo"))
                    .when(smsSender).send(eq(ADMIN_PHONE), anyString());

            tickFarmA();

            assertThat(reminderStatus(morningFeedTask, adminId, Reminder.SMS))
                    .isEqualTo(Reminder.FAILED);
            assertThat(sentAtIsNull(morningFeedTask, adminId, Reminder.SMS)).isTrue();
            assertThat(reminderStatus(morningFeedTask, workerId, Reminder.SMS))
                    .isEqualTo(Reminder.SENT);
            assertThat(sentAtIsNull(morningFeedTask, workerId, Reminder.SMS)).isFalse();
        }

        @Test
        @DisplayName("iliyoshindwa HAIJARIBIWI tena kwenye tiki inayofuata")
        void failureIsNotRetried() {
            // Bei ya makusudi ya kuchagua "isitumwe mara mbili": rekodi ya
            // FAILED inabaki, na UNIQUE inazuia jaribio la pili. Namba mbovu
            // isingegharimu kila tiki milele.
            doThrow(new RuntimeException("provider hayupo"))
                    .when(smsSender).send(eq(ADMIN_PHONE), anyString());

            tickFarmA();
            tickFarmA();

            verify(smsSender, times(3)).send(eq(ADMIN_PHONE), anyString());
            assertThat(reminderStatus(morningFeedTask, adminId, Reminder.SMS))
                    .isEqualTo(Reminder.FAILED);
        }

        @Test
        @DisplayName("push ikilipuka, SMS ya mtu yule yule bado inatumwa")
        void oneChannelFailingDoesNotBlockTheOther() {
            doThrow(new RuntimeException("Pinpoint hayupo"))
                    .when(pushSender).send(anyString(), anyString(), anyString());

            ReminderDispatchService.TickResult result = tickFarmA();

            assertThat(result.failed()).isEqualTo(3);
            assertThat(result.sent()).isEqualTo(6);
            verify(smsSender, times(3)).send(eq(WORKER_PHONE), anyString());
        }
    }

    @Nested
    @DisplayName("saa za kimya na kanda ya saa")
    class QuietHours {

        @Test
        @DisplayName("dirisha linalovuka usiku wa manane (21:00 -> 06:00)")
        void windowCrossesMidnight() {
            assertThat(properties.isQuietAt(LocalTime.of(22, 0))).isTrue();
            assertThat(properties.isQuietAt(LocalTime.of(2, 0))).isTrue();
            assertThat(properties.isQuietAt(LocalTime.of(5, 59))).isTrue();
            assertThat(properties.isQuietAt(LocalTime.of(6, 0))).isFalse();
            assertThat(properties.isQuietAt(LocalTime.of(12, 0))).isFalse();
            assertThat(properties.isQuietAt(LocalTime.of(21, 0))).isTrue();
        }

        @Test
        @DisplayName("tiki inayoanguka ndani ya saa za kimya HAITUMI chochote")
        void quietTickSendsNothing() {
            ReminderDispatchService.TickResult result = dispatch.runTickAt(today, LocalTime.of(23, 30));

            assertThat(result.sent()).isZero();
            verify(smsSender, never()).send(anyString(), anyString());
            verify(pushSender, never()).send(anyString(), anyString(), anyString());
            assertThat(reminderRows()).isZero();
        }

        @Test
        @DisplayName("kanda ni EAT (UTC+3) - Dodoma, si UTC ya server")
        void zoneIsEastAfrica() {
            assertThat(properties.zoneId().getRules()
                    .getOffset(java.time.Instant.now()).getTotalSeconds())
                    .isEqualTo(3 * 60 * 60);
        }
    }
}
