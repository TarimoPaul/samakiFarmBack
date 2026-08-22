package com.samaki.farm.cycle.repository;

import com.samaki.farm.cycle.entity.Cycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CycleRepository extends JpaRepository<Cycle, Integer> {
    List<Cycle> findByUnit_Farm_FarmId(Integer farmId);
    List<Cycle> findByUnit_Farm_FarmIdAndStatus(Integer farmId, String status);
}
