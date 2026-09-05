package com.samaki.farm.feed.repository;

import com.samaki.farm.feed.entity.FeedType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedTypeRepository extends JpaRepository<FeedType, Integer> {

    List<FeedType> findAllByOrderByNameAsc();

    /**
     * Zinazotumika pekee. Ni derived query (si findAll + filter ya Java) ili
     * @SQLRestriction ya soft-delete itumike pia - findAll ingeleta hata
     * zilizofutwa kwa njia ya findById (angalia BaseEntity javadoc).
     */
    List<FeedType> findByActiveTrueOrderByNameAsc();

    /** feed_types.name ni UNIQUE (V16), hivyo jina ni kitambulisho salama cha idempotency. */
    Optional<FeedType> findByName(String name);
}
