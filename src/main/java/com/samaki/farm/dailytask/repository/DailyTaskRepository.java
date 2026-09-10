package com.samaki.farm.dailytask.repository;

import com.samaki.farm.dailytask.entity.DailyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DailyTaskRepository extends JpaRepository<DailyTask, Integer> {

    /**
     * Violezo vya mzunguko mmoja, vikiwa vimepangwa kwa saa ya siku.
     *
     * Mpangilio ni (scheduled_time, task_id) - si scheduled_time pekee:
     * violezo viwili vya saa moja vingepangwa kwa bahati nasibu kila
     * query, na orodha ya kazi za leo inayobadilika mpangilio kila
     * refresh ni ya kuchanganya shambani. Ni hoja ile ile ya
     * WaterQualityLogRepository.
     */
    List<DailyTask> findByCycle_CycleIdOrderByScheduledTimeAscTaskIdAsc(Integer cycleId);

    /**
     * KAZI ZILIZOBAKI za SHAMBA ZIMA kwa siku moja - swali la Reminders.
     *
     * =================================================================
     * NI SHERIA ILE ILE YA TASK COMPLETIONS, KWA UPEO TOFAUTI
     *
     * DailyTaskService.statusForCycle inajibu "kazi za MZUNGUKO huu ni
     * zipi, na kila moja ina hali gani?" kwa MTUMIAJI ALIYEINGIA.
     * Scheduler inauliza swali lingine: "kwenye SHAMBA hili, ni zipi
     * bado hazijafanyika?" - bila mtumiaji yeyote, kwa sababu tiki ya
     * saa 7 asubuhi haina mtu aliyeingia.
     *
     * Kwa hivyo query ipo hapa badala ya kuita huduma ile: kuiita
     * kungehitaji PermissionChecker, ambaye angetupa UNAUTHENTICATED
     * kwenye thread ya background. SHERIA hata hivyo ni ile ile,
     * neno kwa neno:
     *
     *     OUTSTANDING = kiolezo kipo, NA hakuna rekodi ya
     *                   task_completions kwa (task_id, D) yenye DONE
     *
     * 'DONE' imeandikwa hapa kama maandishi kwa sababu JPQL haiwezi
     * kusoma TaskCompletion.DONE; ikibadilika huko, LAZIMA ibadilike
     * hapa - na test ya Kundi E inashika hilo.
     * =================================================================
     *
     * UPEO WA SHAMBA umo ndani ya query yenyewe (`unit.farm.farmId`),
     * si kwenye kichujio cha Java baada ya kusoma. Ndicho kinachozuia
     * kuvuja kati ya mashamba kwenye njia hii: hakuna hatua ambapo
     * kazi za shamba jingine zimeshawahi kuwa mkononi.
     *
     * MIZUNGUKO ILIYOKWISHA imeachwa nje (`status = 'ACTIVE'`). Kiolezo
     * cha daily_tasks hakina tarehe ya mwisho, hivyo bila kichujio hiki
     * mzunguko uliovunwa mwaka jana ungeendelea kuzalisha vikumbusho vya
     * kulisha samaki wasiokuwepo - milele.
     */
    @Query("""
            select t from DailyTask t
            where t.cycle.unit.farm.farmId = :farmId
              and t.cycle.status = 'ACTIVE'
              and not exists (
                  select 1 from TaskCompletion c
                  where c.task = t
                    and c.completionDate = :date
                    and c.status = 'DONE')
            order by t.scheduledTime asc, t.taskId asc
            """)
    List<DailyTask> findOutstandingForFarm(@Param("farmId") Integer farmId,
                                           @Param("date") LocalDate date);

    /**
     * KAZI ZOTE za shamba zima kwa mizunguko INAYOENDELEA - swali la
     * mfanyakazi anayefungua simu asubuhi.
     *
     * =================================================================
     * NI DADA WA findOutstandingForFarm, TOFAUTI KWA KITU KIMOJA
     *
     * Ile inarudisha ZILIZOBAKI pekee (`not exists ... DONE`) kwa sababu
     * Reminders haina haja ya kukumbusha kazi iliyofanyika. Hii
     * INAZIRUDISHA ZOTE - kichujio hicho hakipo hapa kwa makusudi.
     *
     * Sababu ni ile ile iliyofanya statusForCycle isirudishe zilizobaki
     * pekee: mfanyakazi anayeona orodha yake anahitaji KUONA kwamba
     * aliyoifanya imeandikwa. Orodha inayoondoa kazi mara tu
     * inapokamilishwa inamnyima ushahidi pekee alionao, na inamfanya
     * ashindwe kutofautisha "nimeifanya" na "nimeisahau".
     *
     * `done` HAIKOKOTOLEWI hapa: rekodi zinasomwa kwa query MOJA ya
     * pamoja (TaskCompletionRepository.findByTask_TaskIdInAndCompletionDate)
     * na sheria inabaki mahali pake pamoja - DailyTaskService.view.
     * =================================================================
     *
     * KICHUJIO ni kile kile cha findOutstandingForFarm, neno kwa neno,
     * kikiwa kimepewa majina mafupi (`u.farm.farmId` = `t.cycle.unit.farm
     * .farmId`, `c.status` = `t.cycle.status`):
     *
     *   * UPEO WA SHAMBA umo NDANI ya query, si kwenye kichujio cha Java
     *     baada ya kusoma - hakuna hatua ambapo kazi za shamba jingine
     *     zimewahi kuwa mkononi.
     *   * `status = 'ACTIVE'` inaacha nje mizunguko iliyokwisha vunwa.
     *     Kiolezo cha daily_tasks hakina tarehe ya mwisho, hivyo bila
     *     kichujio hiki mzunguko uliovunwa mwaka jana ungeendelea
     *     kuonekana kwenye orodha ya leo - milele.
     *
     * `join fetch` (si path navigation pekee) kwa sababu mkataba sasa
     * unarudisha `unitCode` na `speciesName`: bila kuvuta, kila mzunguko
     * ungezalisha SELECT zake za ziada - N+1 ile ile ambayo
     * findByTask_TaskIdInAndCompletionDate iliwekwa kuizuia upande wa
     * rekodi.
     *
     * MPANGILIO ni (cycle_id, scheduled_time, task_id): shamba lenye
     * mizunguko mitatu lina "Kulisha - Asubuhi" mara tatu, hivyo kuweka
     * mzunguko kwanza kunaziweka pamoja badala ya kuzichanganya kwa saa.
     * task_id ni ya mwisho kwa hoja ile ile ya
     * findByCycle_CycleIdOrderByScheduledTimeAscTaskIdAsc: orodha
     * inayobadilika mpangilio kila refresh ni ya kuchanganya shambani.
     */
    @Query("""
            select t from DailyTask t
            join fetch t.cycle c
            join fetch c.unit u
            join fetch c.species
            where u.farm.farmId = :farmId
              and c.status = 'ACTIVE'
            order by c.cycleId asc, t.scheduledTime asc, t.taskId asc
            """)
    List<DailyTask> findAllForFarm(@Param("farmId") Integer farmId);
}
