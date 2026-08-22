package com.samaki.farm.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope moja ya majibu ya REST API zote (kama Lsms uaa.utils.Response) -
 * controllers zinarudisha hii badala ya entity/DTO raw, ili mteja (frontend)
 * apate muundo ule ule kila wakati awe request imefanikiwa au la.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, String errorCode) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(false, message, null, errorCode);
    }
}
