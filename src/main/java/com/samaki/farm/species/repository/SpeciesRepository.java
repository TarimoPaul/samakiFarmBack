package com.samaki.farm.species.repository;

import com.samaki.farm.species.entity.Species;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeciesRepository extends JpaRepository<Species, Integer> {
}
