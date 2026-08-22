package com.samaki.farm.waterquality.repository;

import com.samaki.farm.waterquality.entity.WaterQualityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaterQualityLogRepository extends JpaRepository<WaterQualityLog, Integer> {

    List<WaterQualityLog> findByUnit_UnitIdOrderByLogDateDesc(Integer unitId);

    /** water_quality_logs haina farm_id - scoping inapitia unit -> farm. */
    List<WaterQualityLog> findByUnit_Farm_FarmIdOrderByLogDateDesc(Integer farmId);
}
