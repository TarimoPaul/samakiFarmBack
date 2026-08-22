package com.samaki.farm.common.exception;

/**
 * Misimbo ya hitilafu inayosomeka na mashine, inayorudishwa kwenye
 * ApiResponse.errorCode. Frontend inatawi kwa hii, SI kwa ujumbe wa
 * Kiswahili (ujumbe unaweza kubadilika/kutafsiriwa; msimbo hauwezi).
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    /** 401 - jibu MOJA kwa password batili, mtu asiyejulikana, na aliyefutwa. */
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    /** 403 - password ni sahihi, lakini akaunti bado haijaidhinishwa. */
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";

    /** 403 - password ni sahihi, lakini akaunti imezuiwa. */
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    /** 429 - maombi mengi mno kwa muda mfupi. */
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
}
