package com.samaki.farm.dailytask.repository;

import com.samaki.farm.dailytask.entity.DailyTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTaskRepository extends JpaRepository<DailyTask, Integer> {
}
