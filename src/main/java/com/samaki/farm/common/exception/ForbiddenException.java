package com.samaki.farm.common.exception;

import lombok.Getter;

/**
 * HTTP 403 yenye errorCode - "tunajua wewe ni nani, lakini huruhusiwi".
 *
 * Inatumika pale mtu ANAPOthibitisha password kwa usahihi lakini akaunti
 * yake haijaidhinishwa au imezuiwa. Tofauti na AccessDeniedException ya
 * Spring (inayotumika kwa ukosefu wa ruhusa), hii inabeba msimbo ambao
 * frontend inautegemea kuamua ionyeshe ukurasa gani.
 */
@Getter
public class ForbiddenException extends RuntimeException {

    private final String errorCode;

    public ForbiddenException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
