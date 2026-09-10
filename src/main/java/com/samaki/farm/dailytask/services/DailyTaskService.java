package com.samaki.farm.dailytask.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.dailytask.dto.CompleteTaskInput;
import com.samaki.farm.dailytask.dto.DailyTaskStatusView;
import com.samaki.farm.dailytask.entity.DailyTask;
import com.samaki.farm.dailytask.entity.TaskCompletion;
import com.samaki.farm.dailytask.repository.DailyTaskRepository;
import com.samaki.farm.dailytask.repository.TaskCompletionRepository;
import com.samaki.farm.reminder.config.ReminderProperties;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module ya kazi za kila siku - kukamilisha kazi, na kusoma zilizobaki.
 *
 * =====================================================================
 * MUUNDO, KAMA SCHEMA YA V1 INAVYOULAZIMISHA
 *
 * `daily_tasks` ni KIOLEZO kinachojirudia: frequency=DAILY,
 * scheduled_time ni saa ya siku, na HAKUNA tarehe ya mwisho popote.
 * Violezo vinazalishwa mara moja kwa mzunguko na
 * CycleService.createDefaultTasks (vitatu: kulisha asubuhi 07:00,
 * kulisha jioni 17:00, kuangalia maji 08:00).
 *
 * Kwa hivyo "imekamilika" HAIWEZI kuwa bendera juu ya kiolezo -
 * ingekuwa kweli milele baada ya siku ya kwanza. Ukamilishaji ni REKODI
 * YAKE, moja kwa (kiolezo, tarehe), na UNIQUE(task_id, completion_date)
 * ya V1 ndiyo inayoulazimisha muundo huo. Si uamuzi uliochaguliwa hapa;
 * ni ule ule uliokwisha kuandikwa kwenye database tangu mwanzo.
 *
 * SHERIA MOJA inayofafanua kila kitu kingine:
 *
 *     OUTSTANDING siku D = kiolezo kipo, NA hakuna rekodi ya
 *                          task_completions kwa (task_id, D) yenye
 *                          status ya DONE
 *
 * Ndiyo mkataba ambao Reminders itausoma (angalia DailyTaskStatusView).
 * =====================================================================
 *
 * SCOPING inapitia PermissionChecker ile ile ya module nyingine.
 * `task_completions` wala `daily_tasks` hazina farm_id: shamba
 * linajulikana kupitia task -> cycle -> unit -> farm, sawa na
 * feeding_logs inavyopitia cycle -> unit -> farm. HAKUNA ukaguzi wa
 * shamba ulioandikwa hapa kwa mkono - ndiyo hasa hitilafu D-1
 * iliyokuwa CycleService.
 */
@Service
public class DailyTaskService {

    /** Ruhusa ya kuandika. Ipo kwenye seed tangu mwanzo; haijaongezwa hapa. */
    private static final String WRITE_PERMISSION = "mark_task_done";

    /** Kusoma ni view_dashboard, kama module ya chakula na ya maji. */
    private static final String READ_PERMISSION = "view_dashboard";

    /**
     * Hali ya kiolezo kisicho na rekodi yoyote kwa siku husika.
     *
     * SI thamani ya database: safu ya task_completions.status ina
     * PENDING/DONE/MISSED/LATE pekee. Hii ni ya mkataba wa kusoma,
     * ikimaanisha "hakuna rekodi kabisa" - hali ambayo ndiyo ya kawaida
     * asubuhi kabla kazi yoyote haijafanyika.
     */
    private static final String OUTSTANDING = "OUTSTANDING";

    private final DailyTaskRepository taskRepository;
    private final TaskCompletionRepository completionRepository;
    private final CycleRepository cycleRepository;
    private final UserRepository userRepository;
    private final PermissionChecker permissionChecker;

    /**
     * KANDA YA SAA, si usanidi wa vikumbusho.
     *
     * Inatumika kwa kitu KIMOJA hapa: kujua "leo" ni tarehe ipi. Ipo
     * kwenye ReminderProperties kwa sababu hapo ndipo ilipoandikwa
     * kwanza (`Africa/Nairobi`), na KUINAKILI hapa ndiko kungekuwa
     * kosa - ingekuwa mahali pa PILI pa kuiweka, na siku moja mmoja
     * angebadilisha moja bila mwenzake.
     *
     * Bila hii ilikuwa `LocalDate.now()` - kanda ya JVM. Server ya ECS/
     * RDS ni UTC, hivyo kati ya saa 6 na saa 9 usiku wa EAT (00:00-03:00)
     * "leo" ilikuwa ikigeuka JANA: mfanyakazi wa alfajiri angeandika
     * kazi yake kwenye tarehe iliyopita, na Scheduler - inayotumia
     * kanda hii hii tayari - ingeendelea kumkumbusha kazi
     * aliyokwisha kuifanya.
     */
    private final ReminderProperties reminderProperties;

    public DailyTaskService(DailyTaskRepository taskRepository,
                            TaskCompletionRepository completionRepository,
                            CycleRepository cycleRepository,
                            UserRepository userRepository,
                            PermissionChecker permissionChecker,
                            ReminderProperties reminderProperties) {
        this.taskRepository = taskRepository;
        this.completionRepository = completionRepository;
        this.cycleRepository = cycleRepository;
        this.userRepository = userRepository;
        this.permissionChecker = permissionChecker;
        this.reminderProperties = reminderProperties;
    }

    /**
     * MKATABA WA REMINDERS: kazi zote za mzunguko kwa siku moja, kila
     * moja ikiwa na `done` yake.
     *
     * Inarudisha KILA kiolezo, si zilizobaki pekee. Swali la "nani
     * hajafanya?" na la "orodha ya leo" ni lile lile likichujwa tofauti,
     * na kurudisha zilizobaki pekee kungemficha mtumiaji kazi
     * alizozimaliza - yaani kuondoa uthibitisho pekee alionao kwamba
     * ameandikwa.
     *
     * Rekodi zinasomwa kwa query MOJA kwa violezo vyote (angalia
     * TaskCompletionRepository) - Reminders itaita hii kwa kila mzunguko
     * wa kila shamba kwa kila tiki.
     */
    @Transactional(readOnly = true)
    public List<DailyTaskStatusView> statusForCycle(Integer cycleId, String date) {
        permissionChecker.requireFarmScope(READ_PERMISSION);

        if (cycleId == null) {
            throw new IllegalArgumentException("cycleId inahitajika.");
        }
        requireCycleInCallersFarm(cycleId);

        LocalDate on = parseDate(date);
        return withStatus(
                taskRepository.findByCycle_CycleIdOrderByScheduledTimeAscTaskIdAsc(cycleId), on);
    }

    /**
     * MWONEKANO WA MFANYAKAZI: kazi za LEO za MIZUNGUKO YOTE
     * INAYOENDELEA ya shamba lake, kila moja ikiwa na `done` yake na jina
     * la aliyeikamilisha.
     *
     * =================================================================
     * KWA NINI SI statusForCycle IKIITWA MARA NYINGI
     *
     * Mteja angeweza kuita `cycles(status: "ACTIVE")` kisha
     * `dailyTasks(cycleId)` kwa kila mzunguko - na ndiyo iliyokuwa njia
     * pekee. Ni maswali N+1 kutoka kwenye SIMU, si kutoka kwenye
     * database: shamba lenye mizunguko sita ni safari saba za mtandao
     * kabla skrini ya kwanza ya asubuhi haijajitokeza, kwenye mtandao wa
     * shambani. Hapa ni query MBILI, daima: violezo, kisha rekodi zao.
     *
     * SWALI NI LILE LILE la kila siku, likiwa na UPEO tofauti tu - ndiyo
     * maana `view(...)` ile ile ndiyo inayotumika, na sheria ya
     * "outstanding" haiandikwi tena hapa. Ingekuwa imeandikwa mara ya
     * pili, siku moja mmoja angeibadilisha upande mmoja.
     * =================================================================
     *
     * UPEO WA SHAMBA hautoki kwa mteja: farmId inatoka kwa
     * requireFarmScope (yaani kwenye token), hivyo HAKUNA hoja
     * inayoweza kuombea shamba lingine - tofauti na dailyTasks
     * ambayo inapokea cycleId na kwa hivyo LAZIMA iithibitishe.
     *
     * MIZUNGUKO ILIYOVUNWA imeachwa nje na query yenyewe (angalia
     * DailyTaskRepository.findAllForFarm).
     */
    @Transactional(readOnly = true)
    public List<DailyTaskStatusView> statusForFarm(String date) {
        Integer farmId = permissionChecker.requireFarmScope(READ_PERMISSION);

        LocalDate on = parseDate(date);
        return withStatus(taskRepository.findAllForFarm(farmId), on);
    }

    /**
     * Violezo + rekodi zao za siku moja, kwa query MOJA ya ziada.
     *
     * Ni sehemu ya PAMOJA ya statusForCycle na statusForFarm: zote mbili
     * zina orodha ya violezo na tarehe, na zinatofautiana kwa jinsi
     * orodha ilivyopatikana PEKEE. Ikiwa imeandikwa mara mbili, query ya
     * pamoja - iliyowekwa hasa kuzuia N+1 - ingekuwa rahisi kuisahau
     * upande mmoja.
     */
    private List<DailyTaskStatusView> withStatus(List<DailyTask> tasks, LocalDate on) {
        if (tasks.isEmpty()) {
            return List.of();
        }

        Map<Integer, TaskCompletion> byTaskId = completionRepository
                .findByTask_TaskIdInAndCompletionDate(tasks.stream().map(DailyTask::getTaskId).toList(), on)
                .stream()
                .collect(Collectors.toMap(c -> c.getTask().getTaskId(), Function.identity()));

        return tasks.stream()
                .map(task -> view(task, on, byTaskId.get(task.getTaskId())))
                .toList();
    }

    /**
     * Kuweka kazi kuwa imekamilika siku fulani.
     *
     * KUGONGANA: kazi iliyokwisha kuwa DONE siku hiyo inakataliwa kwa
     * CONFLICT badala ya kuandikwa upya kimyakimya. Kuandika upya
     * kungefuta jina la aliyeifanya kwanza na saa yake - ndio ushahidi
     * wenyewe - na kungemficha mtumiaji kwamba mwenzake alikwisha
     * kuifanya.
     *
     * Rekodi ILIYOPO isiyokuwa DONE (PENDING/MISSED/LATE) INAGEUZWA kuwa
     * DONE, si kukataliwa. UNIQUE inaruhusu rekodi MOJA tu kwa (kiolezo,
     * tarehe), hivyo kuikataa kungefanya kazi iliyoandikwa MISSED
     * isiweze KAMWE kukamilishwa - mtu aliyeifanya kwa kuchelewa
     * asingekuwa na njia ya kuiripoti.
     *
     * TAREHE ZA NYUMA ZINABAKI ZIKIRUHUSIWA, na tarehe za MBELE
     * hazikubaliwi - angalia requireNotInTheFuture.
     *
     * Ukaguzi wa awali HAUCHUKUI nafasi ya UNIQUE: maombi mawili
     * yanayowasili kwa wakati mmoja yote yangepita ukaguzi, na kikwazo
     * cha database ndicho kinachozuia la pili - kikitokeza
     * DataIntegrityViolationException ambayo GraphQlExceptionResolver
     * inaigeuza kuwa CONFLICT ile ile. Ukaguzi upo ili jibu la kawaida
     * liwe na ujumbe unaoeleweka, si kwa sababu database inaaminiwa
     * kidogo.
     */
    @Transactional
    public DailyTaskStatusView complete(CompleteTaskInput input) {
        permissionChecker.requireFarmScope(WRITE_PERMISSION);

        if (input.taskId() == null) {
            throw new IllegalArgumentException("taskId inahitajika.");
        }
        DailyTask task = requireTaskInCallersFarm(input.taskId());
        LocalDate on = requireNotInTheFuture(parseDate(input.completionDate()));

        TaskCompletion completion = completionRepository
                .findByTask_TaskIdAndCompletionDate(task.getTaskId(), on)
                .orElse(null);

        if (completion == null) {
            completion = new TaskCompletion();
            completion.setTask(task);
            completion.setCompletionDate(on);
        } else if (TaskCompletion.DONE.equals(completion.getStatus())) {
            throw new ConflictException(
                    "Kazi hii tayari imewekwa kuwa imekamilika kwa tarehe " + on + ".");
        }

        completion.setStatus(TaskCompletion.DONE);
        completion.setCompletedAt(Instant.now());
        completion.setCompletedBy(currentUser());
        if (input.notes() != null) {
            completion.setNotes(input.notes());
        }

        return view(task, on, completionRepository.save(completion));
    }

    /**
     * Kiolezo kimoja kikiwa kimeunganishwa na rekodi yake (au ukosefu
     * wake) - mahali PEKEE ambapo sheria ya "outstanding" inaandikwa.
     *
     * `done` ni ulinganisho na DONE PEKEE. Rekodi ya PENDING/MISSED/LATE
     * ni kazi ambayo BADO haijafanyika, hivyo inabaki outstanding na
     * Reminders inapaswa kuikumbusha.
     */
    private DailyTaskStatusView view(DailyTask task, LocalDate on, TaskCompletion completion) {
        boolean done = completion != null && TaskCompletion.DONE.equals(completion.getStatus());
        Cycle cycle = task.getCycle();
        return new DailyTaskStatusView(
                task.getTaskId(),
                cycle == null ? null : cycle.getCycleId(),
                // Tanki na samaki - ndivyo mfanyakazi anavyotofautisha
                // "Kulisha - Asubuhi" tatu zinazofanana kwenye orodha ya
                // shamba zima. Null-safe hatua kwa hatua: cycle_id ni
                // nullable kwenye schema, na kiolezo kisicho na mzunguko
                // hakina tanki wala aina.
                cycle == null || cycle.getUnit() == null ? null : cycle.getUnit().getCode(),
                cycle == null || cycle.getSpecies() == null ? null : cycle.getSpecies().getName(),
                task.getTaskType(),
                task.getScheduledTime(),
                task.getFrequency(),
                task.getAssignedRole() == null ? null : task.getAssignedRole().getName(),
                on,
                completion == null ? OUTSTANDING : completion.getStatus(),
                done,
                completion == null ? null : completion.getCompletedAt(),
                completion == null || completion.getCompletedBy() == null
                        ? null : completion.getCompletedBy().getName(),
                completion == null ? null : completion.getNotes());
    }

    /**
     * Kiolezo cha mwombaji. Njia ya shamba ni task -> cycle -> unit ->
     * farm; jedwali la kazi halina farm_id.
     *
     * `cycle_id` ni nullable kwenye schema, hivyo kiolezo kisicho na
     * mzunguko kinawezekana kimuundo. Hakina shamba la kulinganisha
     * nalo, na kukiruhusu kungefanya kila mtu aweze kukigusa - hivyo
     * kinakataliwa badala ya kupitishwa.
     */
    private DailyTask requireTaskInCallersFarm(Integer taskId) {
        DailyTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Kazi haijulikani"));
        if (task.getCycle() == null) {
            throw new IllegalArgumentException(
                    "Kazi hii haina mzunguko, hivyo haiwezi kuhusishwa na shamba.");
        }
        permissionChecker.requireResourceInCallersFarm(task.getCycle().getUnit().getFarm().getFarmId());
        return task;
    }

    /**
     * Mzunguko wa mwombaji. requireResourceInCallersFarm (si
     * requireSameFarm): hii ni data ya uzalishaji, hivyo ruhusa ya
     * kampuni nzima HAIFUNGUI shamba lingine - angalia PermissionChecker.
     */
    private Cycle requireCycleInCallersFarm(Integer cycleId) {
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("Mzunguko haujulikani"));
        permissionChecker.requireResourceInCallersFarm(cycle.getUnit().getFarm().getFarmId());
        return cycle;
    }

    /**
     * TAREHE HAIWEZI KUWA YA KESHO.
     *
     * =================================================================
     * KWA NINI TAREHE ZA NYUMA ZINABAKI, ILHALI ZA MBELE HAZIBAKI
     *
     * Kuandika NYUMA ni jambo la kawaida shambani, na ndiyo maana
     * `completionDate` ilikubaliwa tangu mwanzo (angalia
     * CompleteTaskInput): mfanyakazi anayeandika jioni kazi aliyoifanya
     * asubuhi, au kesho yake baada ya mtandao kukatika, anaripoti kitu
     * KILICHOTOKEA. Kukikataa kungemfanya asiwe na njia yoyote ya
     * kukiripoti - na kazi iliyofanyika ingebaki ikionekana haijafanyika
     * milele.
     *
     * Kuandika MBELE si hivyo hata kidogo: "nilikamilisha kazi ya kesho"
     * si kauli inayoweza kuwa kweli. Rekodi kama hiyo ingefanya
     * `findOutstandingForFarm` iipuuze kazi ya kesho kabla haijafika,
     * hivyo mfanyakazi asingekumbushwa siku yenyewe - na UNIQUE(task_id,
     * completion_date) ingezuia kuiandika kwa usahihi ikifika. Ni kufuta
     * siku moja ya kazi kwa kuandika mstari mmoja.
     * =================================================================
     *
     * Ni IllegalArgumentException (yaani BAD_REQUEST/VALIDATION_ERROR
     * kupitia GraphQlExceptionResolver), si CONFLICT: hakuna
     * kinachogongana, ombi lenyewe ndilo lisilo na maana.
     */
    private LocalDate requireNotInTheFuture(LocalDate on) {
        LocalDate today = today();
        if (on.isAfter(today)) {
            throw new IllegalArgumentException(
                    "Huwezi kukamilisha kazi kwa tarehe ya baadaye (" + on
                            + "). Leo ni " + today + ".");
        }
        return on;
    }

    /**
     * LEO, KWA SAA YA SHAMBANI - si kwa saa ya server.
     *
     * `LocalDate.now()` bila kanda inasoma kanda ya JVM, ambayo kwenye
     * server ya wingu ni UTC. Dodoma ni EAT (UTC+3), hivyo kuanzia saa
     * 6 usiku hadi saa 9 alfajiri kwa saa ya mkulima, UTC bado iko
     * JANA. Tofauti hiyo ya saa tatu ndiyo inayoamua tarehe
     * inayoandikwa kwenye task_completions - na Scheduler tayari
     * inatumia kanda hii hii (angalia ReminderProperties.zone), hivyo
     * kuiacha ingekuwa module mbili zikikubaliana kuhusu "leo" kwa saa
     * 21 kati ya 24.
     */
    private LocalDate today() {
        return LocalDate.now(reminderProperties.zoneId());
    }

    /**
     * Mtindo ule ule wa WaterQualityService: ikiachwa wazi, ni leo -
     * "leo" ya EAT (angalia today()).
     */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return today();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Tarehe si sahihi. Tumia muundo YYYY-MM-DD.");
        }
    }

    private User currentUser() {
        return userRepository.findByUserId(permissionChecker.currentUser().getUserId()).orElse(null);
    }
}
