package com.samaki.farm.common.graphql;

import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.common.exception.ForbiddenException;
import com.samaki.farm.common.exception.TooManyRequestsException;
import com.samaki.farm.common.exception.UnauthorizedException;
import com.samaki.farm.common.web.GlobalExceptionHandler;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kile ambacho GlobalExceptionHandler ni kwa REST, hii ni kwa GraphQL.
 *
 * GlobalExceptionHandler ni @RestControllerAdvice - HAIGUSI /graphql kabisa.
 * Bila darasa hili, kila hitilafu ya resolver (mfano "Kiasi cha chakula
 * lazima kiwe zaidi ya sifuri") ilikuwa inarudi kama:
 *
 *     "message": "INTERNAL_ERROR for <uuid>"
 *
 * - ujumbe halisi umefichwa na Spring GraphQL kwa usalama. Mteja hakuweza
 * kujua kwa nini ombi lake limekataliwa. Hii inatafsiri exceptions zile zile
 * kuwa ErrorType sahihi za GraphQL, zikiwa na ujumbe unaosomeka.
 *
 * Hitilafu zisizotambulika ZINAACHWA (return null) ili zibaki INTERNAL_ERROR
 * na ujumbe wake ufichwe - hatutaki stack trace au ujumbe wa database
 * kumfikia mteja.
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GraphQlExceptionResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        // Msimbo ule ule REST inaoutuma kwa permission-denied (angalia
        // GlobalExceptionHandler.handleAccessDenied). Awali ulikuwa null,
        // hivyo GraphQL ilikuwa na ErrorType.FORBIDDEN pekee bila
        // extensions.errorCode - frontend ilishindwa kutawi mahali pamoja
        // kwa API zote mbili.
        if (ex instanceof AccessDeniedException) {
            return error(ex, env, ErrorType.FORBIDDEN, ErrorCodes.FORBIDDEN);
        }
        if (ex instanceof ForbiddenException fe) {
            return error(ex, env, ErrorType.FORBIDDEN, fe.getErrorCode());
        }
        // errorCode inatoka kwenye exception yenyewe: UNAUTHENTICATED
        // (hakuna kikao) au INVALID_CREDENTIALS (login imeshindwa) - REST
        // inatuma ile ile.
        //
        // classification inabaki UNAUTHORIZED kwa sababu ErrorType ya Spring
        // GraphQL ina thamani TANO tu (BAD_REQUEST, UNAUTHORIZED, FORBIDDEN,
        // NOT_FOUND, INTERNAL_ERROR) - hakuna UNAUTHENTICATED. UNAUTHORIZED
        // ndiyo inayolingana na 401. Maana kamili iko kwenye errorCode, na
        // ndipo frontend inapaswa kutawi.
        if (ex instanceof UnauthorizedException ue) {
            return error(ex, env, ErrorType.UNAUTHORIZED, ue.getErrorCode());
        }
        // Msimbo unatoka kwenye exception yenyewe, si CONFLICT ya jumla:
        // conflict ya kawaida bado inarudisha CONFLICT (ndio chaguo-msingi
        // la ConflictException), lakini ile yenye maana mahususi - mfano
        // OWNER_IMMUTABLE - inaufikisha msimbo wake kwa mteja. Ni sharti
        // API zote mbili zikubaliane: REST inafanya vivyo hivyo tangu
        // GlobalExceptionHandler.handleConflict.
        if (ex instanceof ConflictException ce) {
            return error(ex, env, ErrorType.BAD_REQUEST, ce.getErrorCode());
        }
        if (ex instanceof TooManyRequestsException) {
            return error(ex, env, ErrorType.BAD_REQUEST, ErrorCodes.TOO_MANY_REQUESTS);
        }
        // Ukiukwaji wa vikwazo vya database (rudufu, FK isiyopo). REST
        // tayari iliurudisha kama 409 (GlobalExceptionHandler.
        // handleDataIntegrity); hapa ulikuwa haujashughulikiwa kabisa,
        // hivyo `createProductionUnit` yenye code inayojirudia kwenye
        // shamba moja ilirudi "INTERNAL_ERROR for <uuid>" na fomu ya
        // mteja haikuweza kueleza tatizo (D-2).
        //
        // Ujumbe ni WETU, si ex.getMessage(): ule wa Hibernate/PostgreSQL
        // unabeba SQL na majina ya constraints - undani usiopaswa kumfikia
        // mteja. Ni ule ule REST inaoutuma.
        if (ex instanceof DataIntegrityViolationException) {
            logger.warn("Ukiukwaji wa vikwazo vya database kwenye GraphQL resolver", ex);
            return error(GlobalExceptionHandler.DATA_INTEGRITY_MESSAGE, env,
                    ErrorType.BAD_REQUEST, ErrorCodes.CONFLICT);
        }
        // Validation ya biashara (mfano kiasi hasi, tarehe isiyosomeka,
        // kitambulisho kisichojulikana) - ujumbe wake ni salama kuonyeshwa.
        //
        // errorCode ilikuwa null hapa, ikiacha makosa ya validation kuwa
        // aina PEKEE ya hitilafu ambayo frontend haikuweza kuitambua kwa
        // msimbo (D-6). classification inabaki BAD_REQUEST kama ilivyokuwa.
        if (ex instanceof IllegalArgumentException) {
            return error(ex, env, ErrorType.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR);
        }

        logger.error("Hitilafu isiyotarajiwa kwenye GraphQL resolver", ex);
        return null; // inabaki INTERNAL_ERROR, ujumbe umefichwa
    }

    private GraphQLError error(Throwable ex, DataFetchingEnvironment env, ErrorType type, String code) {
        return error(ex.getMessage() == null ? type.name() : ex.getMessage(), env, type, code);
    }

    private GraphQLError error(String message, DataFetchingEnvironment env, ErrorType type, String code) {
        GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(message);
        if (code != null) {
            builder.extensions(Map.of("errorCode", code));
        }
        return builder.build();
    }
}
