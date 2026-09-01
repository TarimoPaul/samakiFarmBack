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

    public DailyTaskService(DailyTaskRepository taskRepository,
                            TaskCompletionRepository completionRepository,
                            CycleRepository cycleRepository,
                            UserRepository userRepository,
                            PermissionChecker permissionChecker) {
        this.taskRepository = taskRepository;
        this.completionRepository = completionRepository;
        this.cycleRepository = cycleRepository;
        this.userRepository = userRepository;
        this.permissionChecker = permissionChecker;
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
        List<DailyTask> tasks = taskRepository.findByCycle_CycleIdOrderByScheduledTimeAscTaskIdAsc(cycleId);
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
        LocalDate on = parseDate(input.completionDate());

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
        return new DailyTaskStatusView(
                task.getTaskId(),
                task.getCycle() == null ? null : task.getCycle().getCycleId(),
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

    /** Mtindo ule ule wa WaterQualityService: ikiachwa wazi, ni leo. */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
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
