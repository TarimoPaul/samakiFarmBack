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
}
