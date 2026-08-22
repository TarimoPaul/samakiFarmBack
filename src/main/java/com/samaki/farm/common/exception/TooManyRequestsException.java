package com.samaki.farm.common.exception;

/** HTTP 429 - kikomo cha maombi kimezidiwa (angalia common.ratelimit.RateLimiter). */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
