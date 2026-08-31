package com.samaki.farm.waterquality.repository;

import com.samaki.farm.waterquality.entity.WaterQualityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaterQualityLogRepository extends JpaRepository<WaterQualityLog, Integer> {

    /**
     * Mpangilio ni (log_date, log_id) - si log_date pekee.
     *
     * Vipimo kadhaa vya siku moja ni jambo la kawaida kabisa kwenye module
     * hii (asubuhi na jioni), na kwa tarehe pekee mpangilio wao ungekuwa
     * wa bahati nasibu kila query.
     */
    List<WaterQualityLog> findByUnit_UnitIdOrderByLogDateDescLogIdDesc(Integer unitId);

    /** Scoping ya shamba inapitia unit -> farm (water_quality_logs haina farm_id). */
    List<WaterQualityLog> findByUnit_Farm_FarmIdOrderByLogDateDescLogIdDesc(Integer farmId);
}
