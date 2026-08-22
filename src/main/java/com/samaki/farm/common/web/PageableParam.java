package com.samaki.farm.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * DTO ya kawaida ya query params (page/size/search/sortBy/sortDirection) kwa
 * list endpoints (kama Lsms uaa.utils.PageableParam) - controller inapokea
 * hii kama @ModelAttribute badala ya kila endpoint kuandika Pageable binding
 * yake. "search" haitafsiriwi hapa kiotomatiki (kila repository ina fields
 * tofauti za kutafuta) - endpoint husika ndiyo inayoamua kama/vipi kuitumia.
 */
public record PageableParam(Integer page, Integer size, String search, String sortBy, String sortDirection) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    /** Pageable bila default sort field (unsorted isipoombwa sortBy). */
    public Pageable toPageable() {
        return toPageable(null);
    }

    /** Pageable ikiwa na sort field ya default endapo sortBy haikutolewa. */
    public Pageable toPageable(String defaultSortBy) {
        int pageNumber = (page == null || page < 0) ? DEFAULT_PAGE : page;
        int pageSize = (size == null || size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        String sortField = (sortBy == null || sortBy.isBlank()) ? defaultSortBy : sortBy;
        if (sortField == null || sortField.isBlank()) {
            return PageRequest.of(pageNumber, pageSize);
        }

        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(pageNumber, pageSize, Sort.by(direction, sortField));
    }
}
