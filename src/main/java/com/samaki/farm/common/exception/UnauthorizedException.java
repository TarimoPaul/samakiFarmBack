package com.samaki.farm.common.exception;

import lombok.Getter;

/**
 * HTTP 401 - utambulisho haujathibitika. Tofauti na ForbiddenException (403)
 * yenye maana "tunakujua, lakini huruhusiwi".
 *
 * Inabeba errorCode kwa sababu frontend inahitaji kutawi kwa sababu ya
 * kushindwa, si kwa ujumbe wa Kiswahili.
 */
@Getter
public class UnauthorizedException extends RuntimeException {

    private final String errorCode;

    public UnauthorizedException(String message) {
        this(message, ErrorCodes.INVALID_CREDENTIALS);
    }

    public UnauthorizedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
