package com.samaki.farm.common.web;

import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
    }

    // Hizi mbili ndizo zinazoruhusu service kutupa hitilafu bila kujua HTTP:
    // AuthService inatupa ConflictException/UnauthorizedException, na hapa
    // ndipo zinapopewa status codes zile zile zilizokuwa zikirudishwa kwa
    // mkono na AuthController (409/401).
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message.isBlank() ? "Data uliyotuma si sahihi." : message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Ombi (request body) halisomeki - angalia muundo wa JSON."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Operesheni imekiuka vikwazo vya database (mfano: rudufu au uhusiano usiopo)."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        logger.error("Hitilafu isiyotarajiwa kwenye REST layer", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Hitilafu ya ndani ya mfumo. Jaribu tena baadaye."));
    }
}
