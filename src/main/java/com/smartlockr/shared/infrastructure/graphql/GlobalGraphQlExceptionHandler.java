package com.smartlockr.shared.infrastructure.graphql;

import com.smartlockr.fleet.application.exception.LockerNotFoundException;
import com.smartlockr.fleet.application.exception.UnavailableLockerException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.persistence.EntityNotFoundException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;
import java.util.LinkedHashMap;

@Slf4j
@Component
public class GlobalGraphQlExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(@NonNull Throwable ex, @NonNull DataFetchingEnvironment env) {

        // 1. Autenticación (401)
        if (ex instanceof AuthenticationException) {
            return buildError(env, ErrorType.UNAUTHORIZED, "Authentication required.", "UNAUTHORIZED");
        }

        // 2. Autorización (403)
        if (ex instanceof AccessDeniedException) {
            return buildError(env, ErrorType.FORBIDDEN, "Access is denied.", "FORBIDDEN");
        }

        // 3. Not Found (404)
        if (ex instanceof LockerNotFoundException || ex instanceof EntityNotFoundException) {
            return buildError(env, ErrorType.NOT_FOUND, ex.getMessage(), "RESOURCE_NOT_FOUND");
        }

        // 4. Validación (400)
        if (ex instanceof MethodArgumentNotValidException || ex instanceof IllegalArgumentException) {
            return buildError(env, ErrorType.BAD_REQUEST, ex.getMessage(), "VALIDATION_ERROR");
        }

        if (ex instanceof UnavailableLockerException) {
            return buildError(env, ErrorType.BAD_REQUEST, ex.getMessage(), "LOCKER_UNAVAILABLE");
        }

        // 5. Default (500)
        log.error("Unhandled exception in GraphQL resolver for path: {}", env.getExecutionStepInfo().getPath(), ex);
        return buildError(env, ErrorType.INTERNAL_ERROR, "An internal server error occurred.", "INTERNAL_SERVER_ERROR");
    }

    private GraphQLError buildError(DataFetchingEnvironment env, ErrorType errorType, String message, String customErrorCode) {
        LinkedHashMap<String, Object> extensions = new LinkedHashMap<>();
        if (customErrorCode != null) {
            extensions.put("errorCode", customErrorCode);
        }
        extensions.put("timestamp", Instant.now().toString());

        // Classification es importante para clientes como Apollo
        extensions.put("classification", errorType.toString());

        return GraphqlErrorBuilder.newError()
                .errorType(errorType)
                .message(message)
                .path(env.getExecutionStepInfo().getPath())
                .location(env.getField().getSourceLocation())
                .extensions(extensions)
                .build();
    }
}