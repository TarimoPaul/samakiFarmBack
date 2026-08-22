package com.samaki.farm.common.graphql;

import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ForbiddenException;
import com.samaki.farm.common.exception.TooManyRequestsException;
import com.samaki.farm.common.exception.UnauthorizedException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        if (ex instanceof AccessDeniedException) {
            return error(ex, env, ErrorType.FORBIDDEN, null);
        }
        if (ex instanceof ForbiddenException fe) {
            return error(ex, env, ErrorType.FORBIDDEN, fe.getErrorCode());
        }
        if (ex instanceof UnauthorizedException ue) {
            return error(ex, env, ErrorType.UNAUTHORIZED, ue.getErrorCode());
        }
        if (ex instanceof ConflictException) {
            return error(ex, env, ErrorType.BAD_REQUEST, "CONFLICT");
        }
        if (ex instanceof TooManyRequestsException) {
            return error(ex, env, ErrorType.BAD_REQUEST, "TOO_MANY_REQUESTS");
        }
        // Validation ya biashara (mfano kiasi hasi, tarehe isiyosomeka,
        // kitambulisho kisichojulikana) - ujumbe wake ni salama kuonyeshwa.
        if (ex instanceof IllegalArgumentException) {
            return error(ex, env, ErrorType.BAD_REQUEST, null);
        }

        logger.error("Hitilafu isiyotarajiwa kwenye GraphQL resolver", ex);
        return null; // inabaki INTERNAL_ERROR, ujumbe umefichwa
    }

    private GraphQLError error(Throwable ex, DataFetchingEnvironment env, ErrorType type, String code) {
        GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(ex.getMessage() == null ? type.name() : ex.getMessage());
        if (code != null) {
            builder.extensions(Map.of("errorCode", code));
        }
        return builder.build();
    }
}
