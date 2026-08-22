package com.samaki.farm.farm.repository;

import com.samaki.farm.farm.entity.Farm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmRepository extends JpaRepository<Farm, Integer> {
}
