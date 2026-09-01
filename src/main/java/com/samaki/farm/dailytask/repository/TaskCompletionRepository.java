package com.samaki.farm.dailytask.repository;

import com.samaki.farm.dailytask.entity.TaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskCompletionRepository extends JpaRepository<TaskCompletion, Integer> {

    /**
     * Rekodi ya kiolezo kimoja kwa siku moja - ndiyo inayolingana na
     * UNIQUE(task_id, completion_date), hivyo Optional (si List) ndiyo
     * aina sahihi.
     */
    Optional<TaskCompletion> findByTask_TaskIdAndCompletionDate(Integer taskId, LocalDate completionDate);

    /**
     * Rekodi za VIOLEZO VYOTE vya mzunguko kwa siku moja, kwa query MOJA.
     *
     * Ni hii inayozuia N+1 kwenye swali la "outstanding vs DONE": bila
     * yake, orodha ya violezo vitatu ingezalisha maswali matatu ya ziada,
     * na Reminders - itakayoiuliza kwa kila mzunguko wa kila shamba kwa
     * kila tiki - ingezidisha gharama hiyo mara mia.
     */
    List<TaskCompletion> findByTask_TaskIdInAndCompletionDate(
            Collection<Integer> taskIds, LocalDate completionDate);
}
