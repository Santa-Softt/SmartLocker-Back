package com.smartlockr.shared.infrastructure.graphql;

import com.smartlockr.fleet.application.exception.LockerNotFoundException;
import com.smartlockr.fleet.application.exception.MissingBusinessConfigException;
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

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
public class GlobalGraphQlExceptionHandler extends DataFetcherExceptionResolverAdapter {

    private static final List<Rule> RULES = List.of(
            Rule.of(AuthenticationException.class, ErrorType.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required."),
            Rule.of(AccessDeniedException.class, ErrorType.FORBIDDEN, "FORBIDDEN", "Access is denied."),
            Rule.of(LockerNotFoundException.class, ErrorType.NOT_FOUND, "RESOURCE_NOT_FOUND", null),
            Rule.of(EntityNotFoundException.class, ErrorType.NOT_FOUND, "RESOURCE_NOT_FOUND", null),
            Rule.of(IllegalArgumentException.class, ErrorType.BAD_REQUEST, "BAD_REQUEST", null),
            Rule.of(UnavailableLockerException.class, ErrorType.BAD_REQUEST, "LOCKER_UNAVAILABLE", null),
            Rule.of(MissingBusinessConfigException.class, ErrorType.INTERNAL_ERROR, "BUSINESS_UNAVAILABLE",
                    "Business configuration is missing.")
    );

    @Override
    protected GraphQLError resolveToSingleError(@NonNull Throwable ex, @NonNull DataFetchingEnvironment env) {
        Throwable root = unwrap(ex);

        Rule rule = RULES.stream().filter(r -> r.matches(root)).findFirst().orElse(null);
        if (rule == null) {
            log.error("Unhandled exception. path={}", env.getExecutionStepInfo().getPath(), root);
            return error(env, ErrorType.INTERNAL_ERROR, "INTERNAL_SERVER_ERROR", "An internal server error occurred.");
        }

        String fallbackMessage = (rule.type == ErrorType.INTERNAL_ERROR)
                ? "An internal server error occurred."
                : safeMessage(root);

        String message = (rule.publicMessage != null) ? rule.publicMessage : fallbackMessage;

        return error(env, rule.type, rule.code, message);
    }

    private GraphQLError error(DataFetchingEnvironment env, ErrorType type, String code, String message) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(message)
                .extensions(Map.of(
                        "code", code,
                        "timestamp", Instant.now().toString()
                ))
                .build();
    }

    private Throwable unwrap(Throwable ex) {
        if (ex instanceof CompletionException ce && ce.getCause() != null) return ce.getCause();
        if (ex instanceof InvocationTargetException ite && ite.getCause() != null) return ite.getCause();
        return ex;
    }

    private String safeMessage(Throwable ex) {
        return (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Request could not be processed."
                : ex.getMessage();
    }

    private record Rule(
            Class<? extends Throwable> exType,
            ErrorType type,
            String code,
            String publicMessage
    ) {
        boolean matches(Throwable ex) { return exType.isInstance(ex); }
        static Rule of(Class<? extends Throwable> exType, ErrorType type, String code, String publicMessage) {
            return new Rule(exType, type, code, publicMessage);
        }
    }
}