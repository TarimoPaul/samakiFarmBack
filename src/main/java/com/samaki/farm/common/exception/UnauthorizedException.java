package com.samaki.farm.common.exception;

/**
 * HTTP 401 - utambulisho haujathibitika (credentials si sahihi). Tofauti na
 * AccessDeniedException (403) ambayo ina maana "tunakujua, lakini huruhusiwi".
 * Angalia ConflictException kwa maelezo ya kwa nini service zinatupa
 * exceptions badala ya ResponseEntity.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
