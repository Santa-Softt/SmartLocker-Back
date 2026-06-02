package com.smartlockr.shared.infrastructure.graphql;

import com.smartlockr.billing.application.exception.InvalidMPSignatureException;
import com.smartlockr.billing.application.exception.PaymentGatewayException;
import com.smartlockr.billing.application.exception.RentFailedException;
import com.smartlockr.fleet.application.exception.LockerNotFoundException;
import com.smartlockr.fleet.application.exception.MissingBusinessConfigException;
import com.smartlockr.fleet.application.exception.UnavailableLockerException;
import com.smartlockr.rental.application.exception.IllegalLockerChangeStateException;
import com.smartlockr.shared.i18n.MessageResolver;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.i18n.LocaleContextHolder;
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
@RequiredArgsConstructor
public class GlobalGraphQlExceptionHandler extends DataFetcherExceptionResolverAdapter {

    private final ObjectProvider<MessageResolver> messageResolverProvider;

    private static final List<Rule> RULES = List.of(
            Rule.of(RentFailedException.class, ErrorType.BAD_REQUEST, "LOCKER_STATUS_FAILED", "error.rental.failed"),
            Rule.of(AuthenticationException.class, ErrorType.UNAUTHORIZED, "UNAUTHORIZED", "error.auth.unauthorized"),
            Rule.of(AccessDeniedException.class, ErrorType.FORBIDDEN, "FORBIDDEN", "error.auth.forbidden"),
            Rule.of(LockerNotFoundException.class, ErrorType.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.resource.not-found"),
            Rule.of(EntityNotFoundException.class, ErrorType.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.resource.not-found"),
            Rule.of(IllegalArgumentException.class, ErrorType.BAD_REQUEST, "BAD_REQUEST", "error.request.bad"),
            Rule.of(UnavailableLockerException.class, ErrorType.BAD_REQUEST, "LOCKER_UNAVAILABLE", "error.locker.unavailable"),
            Rule.of(MissingBusinessConfigException.class, ErrorType.INTERNAL_ERROR, "BUSINESS_UNAVAILABLE", "error.business.unavailable"),
            Rule.of(PaymentGatewayException.class, ErrorType.INTERNAL_ERROR, "PAYMENT_GATEWAY_UNAVAILABLE", "error.payment.gateway"),
            Rule.of(InvalidMPSignatureException.class, ErrorType.INTERNAL_ERROR, "MP_SIGNATURE_MISMATCH", "error.payment.invalid-signature"),
            Rule.of(IllegalLockerChangeStateException.class, ErrorType.BAD_REQUEST, "LOCKER_NOT_IN_HOLD", "error.rental.invalid-state"),
            Rule.of(ValidationException.class, ErrorType.BAD_REQUEST, "USER_CONSTRAINTS_NOT_ALLOWED", "error.validation.failed")
    );

    @Override
    protected GraphQLError resolveToSingleError(@NonNull Throwable ex, @NonNull DataFetchingEnvironment env) {
        Throwable root = unwrap(ex);

        Rule rule = RULES.stream()
                .filter(r -> r.matches(root))
                .findFirst().orElse(null);

        if (rule == null) {
            log.error("Unhandled exception. path={}", env.getExecutionStepInfo().getPath(), root);
            return error(env, ErrorType.INTERNAL_ERROR, "INTERNAL_SERVER_ERROR", resolve("error.internal"));
        }
        return error(env, rule.type, rule.code, resolve(rule.messageCode));
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

    private String resolve(String code) {
        MessageResolver messageResolver = messageResolverProvider.getIfAvailable();
        return messageResolver == null ? code : messageResolver.resolve(code, LocaleContextHolder.getLocale());
    }

    private record Rule(
            Class<? extends Throwable> exType,
            ErrorType type,
            String code,
            String messageCode
    ) {
        boolean matches(Throwable ex) { return exType.isInstance(ex); }
        static Rule of(Class<? extends Throwable> exType, ErrorType type, String code, String messageCode) {
            return new Rule(exType, type, code, messageCode);
        }
    }
}
