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

    /**
     * 401 - HUJAJITAMBULISHA kabisa: token haipo, imeisha muda, au si sahihi.
     *
     * Ni TOFAUTI na INVALID_CREDENTIALS kimakusudi. Huo unamaanisha
     * "umejaribu kuingia na simu/password zisizolingana" - frontend
     * inaonyesha kosa kwenye fomu ya login. Huu unamaanisha "hakuna
     * kikao hata kidogo" - frontend inaelekeza kwenye ukurasa wa login
     * na kufuta token iliyohifadhiwa. Kuzichanganya kungefanya token
     * iliyoisha muda ionekane kama password mbaya.
     *
     * Unatoka sehemu mbili zinazopaswa kutoa jibu MOJA:
     *  - SecurityConfig.authenticationEntryPoint - ombi kwenye endpoint
     *    iliyolindwa bila token halali (linakatwa kwenye filter chain).
     *  - PermissionChecker.currentUser() - principal haipo/si ya aina
     *    sahihi wakati controller au resolver inaomba mtumiaji wa sasa.
     */
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";

    /** 403 - password ni sahihi, lakini akaunti bado haijaidhinishwa. */
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";

    /** 403 - password ni sahihi, lakini akaunti imezuiwa. */
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    /**
     * 403 - token ni halali, lakini mtu analazimika kubadilisha password
     * kabla ya kutumia mfumo (angalia JwtAuthFilter). Frontend ikiona hii
     * inaelekeza kwenye ukurasa wa kubadilisha password.
     */
    public static final String MUST_CHANGE_PASSWORD = "MUST_CHANGE_PASSWORD";

    /**
     * 403 - umeingia na akaunti yako ni salama, lakini huna ruhusa ya
     * kufanya jambo hili (RBAC).
     *
     * Msimbo MMOJA kwa kila permission-denied, REST na GraphQL: REST
     * inaurudisha kwenye ApiResponse.errorCode (angalia
     * GlobalExceptionHandler.handleAccessDenied na SecurityConfig), GraphQL
     * kwenye extensions.errorCode pamoja na ErrorType.FORBIDDEN (angalia
     * GraphQlExceptionResolver). Frontend inatawi mahali pamoja kwa API
     * zote mbili.
     *
     * HAUCHUKUI nafasi ya misimbo ya hali ya akaunti hapo juu
     * (ACCOUNT_DISABLED / PENDING_APPROVAL / MUST_CHANGE_PASSWORD).
     * Hiyo inaandikwa na JwtAuthFilter ndani ya filter chain, ombi
     * likizuiwa kabla halijafika DispatcherServlet - hivyo huu hauwezi
     * kuifunika.
     */
    public static final String FORBIDDEN = "FORBIDDEN";

    /** 429 - maombi mengi mno kwa muda mfupi. */
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";

    /**
     * 409 - ombi limegongana na data iliyopo: rudufu (mfano code ya tanki
     * inayojirudia kwenye shamba moja), au uhusiano usiokidhi masharti ya
     * database.
     *
     * Awali GraphQL ILIKUWA HAIUTUMI kabisa - DataIntegrityViolationException
     * haikuwa na tawi kwenye GraphQlExceptionResolver, hivyo ilishuka hadi
     * INTERNAL_ERROR na ujumbe wake ukafichwa. Mteja alipata
     * "INTERNAL_ERROR for <uuid>" badala ya kujua kwamba code
     * ameiandika tayari ipo (angalia FRONTEND_BACKEND_AUDIT.md, D-2).
     */
    public static final String CONFLICT = "CONFLICT";

    /**
     * 400 - data iliyotumwa haikubaliki kibiashara (kiasi hasi, tarehe
     * isiyosomeka, kitambulisho kisichojulikana).
     *
     * Ipo kwa sababu makosa haya YALIKUWA yanarudi bila errorCode yoyote
     * kwenye GraphQL (null), ilhali sheria ya frontend ni "tawi kwa
     * errorCode, KAMWE si kwa ujumbe" - hivyo hapakuwa na cha kutawia kwa
     * makosa ya validation (D-6). Ujumbe wenyewe unabaki ukielezea NINI
     * hasa kimekataliwa.
     */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
}
