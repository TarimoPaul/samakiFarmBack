package com.samaki.farm.dailytask.entity;

import com.samaki.farm.user.entity.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Ushahidi kwamba kazi ya kila siku ILIFANYIKA siku fulani.
 *
 * SAFU ni zile zile za V1 / ERD / Data Dictionary - hakuna iliyoongezwa:
 * task_id, completion_date, completed_by_user_id, completed_at, status,
 * notes, pamoja na UNIQUE(task_id, completion_date).
 *
 * HAIRITHI BaseEntity, tofauti na entities nyingine zote za module hii.
 * task_completions ni jedwali PEKEE kati ya haya lisilokuwa na safu za
 * audit/soft-delete: V2 iliziongeza kwenye roles/permissions/cycles/
 * daily_tasks/production_units/species na kuliruka hili. Kurithi
 * BaseEntity kungefanya `ddl-auto: validate` ikatae kuanza kabisa, na
 * kuongeza safu hizo kungekuwa kubuni schema - kitu ambacho maelekezo
 * yanakataza. Kwa vitendo hazikosekani: completed_at na
 * completed_by_user_id tayari ndizo audit ya rekodi hii, na rekodi
 * yenyewe haifutwi bali inagongana (angalia UNIQUE).
 *
 * KWA NINI RIPOTI NI SAFU YAKE, si `daily_tasks.status`: daily_tasks ni
 * KIOLEZO kinachojirudia (frequency=DAILY, scheduled_time ni saa ya
 * siku, hakuna tarehe ya mwisho - angalia CycleService.createDefaultTasks).
 * Kiolezo kimoja kinatakiwa kufanyika KILA siku, hivyo "imekamilika"
 * haiwezi kuwa bendera juu yake: ingekuwa kweli milele baada ya siku ya
 * kwanza. UNIQUE(task_id, completion_date) ndiyo inayoiweka rekodi
 * mahali sahihi - moja kwa kiolezo kwa siku.
 */
@Entity
@Table(name = "task_completions")
@Data
@ToString(exclude = {"task", "completedBy"})
public class TaskCompletion {

    /** Kazi imefanyika. Ndiyo pekee inayohesabika kama "si outstanding". */
    public static final String DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "completion_id")
    private Integer completionId;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private DailyTask task;

    @Column(name = "completion_date", nullable = false)
    private LocalDate completionDate;

    @ManyToOne
    @JoinColumn(name = "completed_by_user_id")
    // MTU aliyekamilisha, si uanachama - completed_by_user_id inaelekea
    // `users`, mtindo ule ule wa water_quality_logs.recorded_by_user_id.
    private User completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * PENDING / DONE / MISSED / LATE - orodha ni ya V1, si mpya.
     *
     * Hakuna kinachoandika PENDING kwa sasa (rekodi inazaliwa ikiwa DONE),
     * lakini safu inabaki wazi kwa hali nyingine kwa sababu schema
     * inaziruhusu, na huduma inajua kuzigeuza kuwa DONE badala ya
     * kuzikataa (angalia DailyTaskService.complete).
     */
    @Column(name = "status", nullable = false)
    private String status = DONE;

    @Column(name = "notes")
    private String notes;
}
