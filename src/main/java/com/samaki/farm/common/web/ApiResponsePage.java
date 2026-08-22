package com.samaki.farm.common.web;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envelope ya majibu ya list endpoints zenye pagination (kama Lsms
 * uaa.utils.ResponsePage) - sawa dhana na ApiResponse<T> lakini inaongeza
 * metadata ya ukurasa moja kwa moja kutoka Spring Data Page<T>, badala ya
 * kila controller kuandika mapping yake.
 */
public record ApiResponsePage<T>(boolean success, List<T> data, int page, int size,
                                  long totalElements, int totalPages, boolean hasNext, boolean hasPrevious) {

    public static <T> ApiResponsePage<T> of(Page<T> page) {
        return new ApiResponsePage<>(
                true,
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
