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
     * 403 - umeingia, una ruhusa husika, LAKINI huna shamba - hivyo ombi
     * la data ya shamba halina maana. Kwa vitendo huyu ni ROOT (akaunti ya
     * usimamizi, haina farmId), au mtu aliyeidhinishwa ambaye bado
     * hajapangiwa shamba lolote.
     *
     * Awali hali hii ilikuwa KIMYA na inapotosha: query zilirudisha [] au
     * 0.0 - zikionekana kama "shamba lako halina kitu" badala ya "huna
     * shamba" - na mutations zilianguka kwa INTERNAL_ERROR isiyoeleweka
     * pale farmId null ilipofika kwenye findById (angalia
     * FRONTEND_BACKEND_AUDIT.md, D-9).
     */
    public static final String NO_FARM_CONTEXT = "NO_FARM_CONTEXT";

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
     * 409 - mmiliki wa shamba hawezi kutolewa kwenye shamba lake
     * (FarmUserService.removeMembership).
     *
     * Ni CONFLICT yenye maana MAHUSUSI. Ipo kama msimbo wake kwa sababu
     * ndicho kikwazo pekee cha kudumu kwenye uanachama, na frontend
     * inahitaji kutofautisha "hii haitawezekana kamwe kwa mtu huyu" na
     * rudufu ya kawaida ambayo mtumiaji anaweza kuirekebisha na kujaribu
     * tena. Ujumbe wa Kiswahili unabaki ule ule - unaeleza NINI, msimbo
     * unaeleza AINA.
     *
     * Mteja anayejua CONFLICT pekee bado anafanya kazi: 409 ni ile ile,
     * na msimbo huu ni nyongeza, si mbadala.
     */
    public static final String OWNER_IMMUTABLE = "OWNER_IMMUTABLE";

    /**
     * 409 - nafasi haiwezi kufutwa kwa sababu bado inashikiliwa na watu
     * (RoleService.deleteRole).
     *
     * Ni CONFLICT yenye maana MAHUSUSI, kama OWNER_IMMUTABLE - lakini
     * TOFAUTI naye kwa jambo moja la msingi: hii INAWEZA kupita baadaye.
     * Msimamizi akiwabadilishia watu hao nafasi nyingine, ombi lilelile
     * linafanikiwa. Ndiyo maana ujumbe unataja IDADI yao: unamweleza
     * kilichobaki kufanywa, si kwamba amekwama.
     *
     * Frontend inautumia kupendekeza njia nyingine ("izime badala ya
     * kuifuta") - jambo ambalo CONFLICT ya jumla isingeweza kubeba.
     */
    public static final String ROLE_IN_USE = "ROLE_IN_USE";

    /**
     * 409 - shamba haliwezi kufutwa kwa sababu bado lina wanachama
     * (FarmService.delete).
     *
     * Kama ROLE_IN_USE: kikwazo kinachopitika, si sheria ya kudumu -
     * wanachama wakiondolewa, ombi lilelile linafanikiwa, na ujumbe unataja
     * idadi yao ili msimamizi ajue kilichobaki.
     *
     * KWA NINI kikwazo kabisa: soft-delete ya shamba huacha safu za
     * `farm_users` zikielekeza kwenye shamba ambalo kila query inalificha.
     * Watu hao wangebaki na uanachama usio na shamba - kikao chao kikiwa
     * kinaelekeza mahali pasipoonekana - na hakuna skrini yoyote
     * ingeeleza kwa nini.
     */
    public static final String FARM_IN_USE = "FARM_IN_USE";

    /**
     * 409 - aina ya chakula haiwezi kufutwa kwa sababu bado inatumika
     * (FeedService.deleteFeedType).
     *
     * Familia ile ile ya ROLE_IN_USE na FARM_IN_USE, na kwa sababu ILE ILE
     * ya kiufundi: kufuta ni SOFT, hivyo safu za feeding_logs,
     * feed_purchases na feed_stock_movements zingebaki zikielekeza kwenye
     * aina ambayo @SQLRestriction inaificha kwenye kila query. Matokeo si
     * data iliyofichwa tu - `FeedingLog.feedType` ni `FeedType!` kwenye
     * schema, hivyo historia YOTE ya ulishaji ya shamba ingekataa kusomeka,
     * si mstari mmoja tu.
     *
     * Kinachopitika, kama ROLE_IN_USE: aina isiyowahi kutumiwa inafutika
     * mara moja. Ujumbe unataja IDADI ya rekodi zinazoizuia, na
     * unapendekeza njia ya pili - KUIZIMA - ambayo ndiyo iliyokusudiwa na
     * V16 tangu mwanzo kwa aina inayoachwa kutumika.
     */
    public static final String FEED_TYPE_IN_USE = "FEED_TYPE_IN_USE";

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
