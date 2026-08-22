package com.samaki.farm.common.exception;

/**
 * HTTP 409 - ombi linagongana na hali iliyopo (mfano namba ya simu
 * iliyosajiliwa tayari).
 *
 * Service zinatupa exception badala ya kurudisha ResponseEntity yenye status:
 * ndiyo inayoziruhusu kuitwa na REST controller NA GraphQL resolver kwa
 * namna ile ile - hakuna dhana ya HTTP status ndani ya service.
 * Ramani ya exception -> status iko GlobalExceptionHandler.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
