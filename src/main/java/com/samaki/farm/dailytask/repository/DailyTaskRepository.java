package com.samaki.farm.dailytask.repository;

import com.samaki.farm.dailytask.entity.DailyTask;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
