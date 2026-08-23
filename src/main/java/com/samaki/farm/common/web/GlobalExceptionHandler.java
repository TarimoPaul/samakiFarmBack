package com.samaki.farm.common.web;

import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.common.exception.ForbiddenException;
import com.samaki.farm.common.exception.TooManyRequestsException;
import com.samaki.farm.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Handler mmoja kwa exceptions zote za REST controllers (kama Lsms
 * uaa.exception.GlobalExceptionHandler) - kila hitilafu inarudi kama
 * ApiResponse.error(...) badala ya kila controller kuandika try/catch yake
 * au kuvuja stack trace/default error page kwenda kwa mteja.
 *
 * Haiathiri GraphQL - resolvers (CycleResolver/ProductionUnitResolver) zina
 * utaratibu wao wa makosa kupitia GraphQL spec (errors[] array), si HTTP
 * status codes.
 *
 * AccessDeniedException hapa inashughulikia zote mbili: (1) @PreAuthorize
 * failures, (2) PermissionChecker.require()/requireSameFarm() zinazotupwa
 * kwa mkono ndani ya controller method - zote zinatokea wakati wa
 * DispatcherServlet kuchakata request, hivyo zinakamatwa hapa kabla
 * hazijafika kwenye Spring Security's ExceptionTranslationFilter (angalia
 * SecurityConfig kwa 401/403 za filter-chain level, kabla ya kufika hapa -
 * mfano token isiyokuwepo kabisa).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Ujumbe wa kurudi pale exception haina wetu - sawa na wa SecurityConfig. */
    static final String FORBIDDEN_MESSAGE = "Huna ruhusa ya kufikia rasilimali hii.";

    /**
     * Ujumbe WETU kwa ukiukwaji wa vikwazo vya database. Wa Hibernate/
     * PostgreSQL unabeba SQL na majina ya constraints, hivyo haumfikii
     * mteja. Ni public kwa sababu GraphQlExceptionResolver inautumia ule
     * ule - ombi lilelile lieleze kitu kilekile likipita REST au GraphQL.
     */
    public static final String DATA_INTEGRITY_MESSAGE =
            "Operesheni imekiuka vikwazo vya database (mfano: rudufu au uhusiano usiopo).";

    /**
     * Permission-denied ya kawaida (RBAC) - 403 + ErrorCodes.FORBIDDEN.
     *
     * Vyanzo viwili, ujumbe wa aina mbili:
     *
     *  - @PreAuthorize inatupa AuthorizationDeniedException yenye ujumbe wa
     *    ndani wa Spring, "Access Denied" - Kiingereza, hauelezi lolote, na
     *    ni undani wa framework usiopaswa kumfikia mteja. Unabadilishwa.
     *  - PermissionChecker.require()/requireSameFarm() zinatupa
     *    AccessDeniedException yenye ujumbe WETU wa Kiswahili ("Huna ruhusa
     *    ya 'manage_farms'."). Huo unahifadhiwa - ndio ule ule
     *    GraphQlExceptionResolver inautuma, hivyo ombi lile lile linaeleza
     *    kitu kile kile likipita REST au GraphQL.
     *
     * HAIGUSI hali ya akaunti: ACCOUNT_DISABLED / PENDING_APPROVAL /
     * MUST_CHANGE_PASSWORD zinaandikwa na JwtAuthFilter, ambayo inakata ombi
     * ndani ya filter chain na HAIITI chain.doFilter() - hivyo halifiki
     * DispatcherServlet wala handler hii. Misimbo hiyo inabaki ikishinda.
     * (ForbiddenException ni RuntimeException, si AccessDeniedException,
     * hivyo handleForbidden hapa chini nayo haigongani na hii.)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        String message = (ex instanceof AuthorizationDeniedException) ? null : ex.getMessage();
        if (message == null || message.isBlank()) {
            message = FORBIDDEN_MESSAGE;
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(message, ErrorCodes.FORBIDDEN));
    }

    // Hizi mbili ndizo zinazoruhusu service kutupa hitilafu bila kujua HTTP:
    // AuthService inatupa ConflictException/UnauthorizedException, na hapa
    // ndipo zinapopewa status codes zile zile zilizokuwa zikirudishwa kwa
    // mkono na AuthController (409/401).
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    // errorCode inapitishwa hapa: frontend inatawi kwa msimbo
    // (PENDING_APPROVAL / ACCOUNT_DISABLED / INVALID_CREDENTIALS), si kwa
    // ujumbe wa Kiswahili unaoweza kubadilika.
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage(), ErrorCodes.TOO_MANY_REQUESTS));
    }

    // Status zinabaki zilezile (400/409). Kilichoongezwa ni errorCode:
    // GraphQL sasa inatuma VALIDATION_ERROR/CONFLICT kwa makosa yale yale
    // (angalia GraphQlExceptionResolver), na sheria ya frontend ni kutawi
    // kwa msimbo kwa API ZOTE MBILI - hivyo REST nayo lazima iutume.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage(), ErrorCodes.VALIDATION_ERROR));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message.isBlank() ? "Data uliyotuma si sahihi." : message,
                        ErrorCodes.VALIDATION_ERROR));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Ombi (request body) halisomeki - angalia muundo wa JSON.",
                        ErrorCodes.VALIDATION_ERROR));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(DATA_INTEGRITY_MESSAGE, ErrorCodes.CONFLICT));
    }

    /**
     * Njia isiyokuwepo -> 404, si 500.
     *
     * Bila handler hii zilikuwa zinaangukia handleGeneric hapa chini
     * (Exception.class), hivyo URL yoyote iliyoandikwa vibaya ilirudisha
     * "Hitilafu ya ndani ya mfumo" - ikimwambia mteja kwamba server
     * imeharibika wakati kwa kweli YEYE ndiye ameomba kitu kisichokuwepo,
     * na ikijaza logs kwa ERROR zisizo za kweli.
     *
     * Ilionekana wakati wa kuthibitisha D-5: /actuator/health (actuator
     * haipo kwenye pom.xml) ilikuwa inarudisha 500 kwa mwenye token.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Njia hii haipo."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        logger.error("Hitilafu isiyotarajiwa kwenye REST layer", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Hitilafu ya ndani ya mfumo. Jaribu tena baadaye."));
    }
}
