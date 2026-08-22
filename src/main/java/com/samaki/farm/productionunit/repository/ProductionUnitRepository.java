package com.samaki.farm.productionunit.repository;

import com.samaki.farm.productionunit.entity.ProductionUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductionUnitRepository extends JpaRepository<ProductionUnit, Integer> {
    List<ProductionUnit> findByFarm_FarmId(Integer farmId);
}
