package com.samaki.farm.common.exception;

/**
 * HTTP 409 - ombi linagongana na hali iliyopo (mfano namba ya simu
 * iliyosajiliwa tayari).
 *
 * Service zinatupa exception badala ya kurudisha ResponseEntity yenye status:
 * ndiyo inayoziruhusu kuitwa na REST controller NA GraphQL resolver kwa
 * namna ile ile - hakuna dhana ya HTTP status ndani ya service.
 * Ramani ya exception -> status iko GlobalExceptionHandler.
 *
 * MSIMBO: kila conflict sasa inabeba errorCode. Ile isiyotaja yake
 * mahususi inapata ErrorCodes.CONFLICT - si null, kama ilivyokuwa awali.
 * Hii ilikuwa hitilafu pekee ya backend hii iliyorudi BILA errorCode
 * ilhali sheria iliyotangazwa kwa frontend ni "tawi kwa errorCode, KAMWE
 * si kwa ujumbe"; matokeo yake skrini za Members na Approvals zililazimika
 * kutawi kwa status 409 - njia tofauti na hitilafu nyingine zote
 * (angalia CONFLICT_STATUS kwenye repo ya frontend).
 *
 * GraphQL tayari ilikuwa ikituma ErrorCodes.CONFLICT kwa exception hii
 * (GraphQlExceptionResolver), hivyo mabadiliko haya yanaziunganisha API
 * mbili badala ya kuzitenganisha zaidi.
 */
public class ConflictException extends RuntimeException {

    private final String errorCode;

    public ConflictException(String message) {
        this(message, ErrorCodes.CONFLICT);
    }

    /** Kwa conflict yenye maana mahususi - mfano ErrorCodes.OWNER_IMMUTABLE. */
    public ConflictException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode == null ? ErrorCodes.CONFLICT : errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
