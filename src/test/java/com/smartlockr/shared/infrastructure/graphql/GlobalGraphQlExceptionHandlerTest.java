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
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalGraphQlExceptionHandlerTest {

    @Mock
    private ObjectProvider<MessageResolver> messageResolverProvider;

    @Mock
    private MessageResolver messageResolver;

    private DataFetchingEnvironment env;
    private GlobalGraphQlExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalGraphQlExceptionHandler(messageResolverProvider);
        when(messageResolverProvider.getIfAvailable()).thenReturn(messageResolver);
        lenient().when(messageResolver.resolve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Locale.class)))
                .thenAnswer(inv -> "msg:" + inv.getArgument(0));
        env = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .mergedField(MergedField.newMergedField(new Field("testField")).build())
                .executionStepInfo(mock(ExecutionStepInfo.class))
                .build();
    }

    static Stream<Arguments> ruleMappings() {
        return Stream.of(
                Arguments.of(new RentFailedException("x"), ErrorType.BAD_REQUEST, "LOCKER_STATUS_FAILED", "error.rental.failed"),
                Arguments.of(mock(AuthenticationException.class), ErrorType.UNAUTHORIZED, "UNAUTHORIZED", "error.auth.unauthorized"),
                Arguments.of(new AccessDeniedException("x"), ErrorType.FORBIDDEN, "FORBIDDEN", "error.auth.forbidden"),
                Arguments.of(new LockerNotFoundException("x"), ErrorType.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.resource.not-found"),
                Arguments.of(new EntityNotFoundException("x"), ErrorType.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.resource.not-found"),
                Arguments.of(new IllegalArgumentException("x"), ErrorType.BAD_REQUEST, "BAD_REQUEST", "error.request.bad"),
                Arguments.of(new UnavailableLockerException("x"), ErrorType.BAD_REQUEST, "LOCKER_UNAVAILABLE", "error.locker.unavailable"),
                Arguments.of(new MissingBusinessConfigException("x"), ErrorType.INTERNAL_ERROR, "BUSINESS_UNAVAILABLE", "error.business.unavailable"),
                Arguments.of(new PaymentGatewayException("x"), ErrorType.INTERNAL_ERROR, "PAYMENT_GATEWAY_UNAVAILABLE", "error.payment.gateway"),
                Arguments.of(new InvalidMPSignatureException("x"), ErrorType.INTERNAL_ERROR, "MP_SIGNATURE_MISMATCH", "error.payment.invalid-signature"),
                Arguments.of(new IllegalLockerChangeStateException("x"), ErrorType.BAD_REQUEST, "LOCKER_NOT_IN_HOLD", "error.rental.invalid-state"),
                Arguments.of(new ValidationException("x"), ErrorType.BAD_REQUEST, "USER_CONSTRAINTS_NOT_ALLOWED", "error.validation.failed")
        );
    }

    @ParameterizedTest
    @MethodSource("ruleMappings")
    @DisplayName("resolveToSingleError - mapea excepciones conocidas a codigos y tipos correctos")
    void shouldMapKnownExceptions(Throwable ex, ErrorType expectedType, String expectedCode, String expectedMessageCode) {
        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error).isNotNull();
        assertThat(error.getErrorType()).isEqualTo(expectedType);
        Map<String, Object> extensions = error.getExtensions();
        assertThat(extensions).containsEntry("code", expectedCode);
        assertThat(error.getMessage()).isEqualTo("msg:" + expectedMessageCode);
    }

    @Test
    @DisplayName("resolveToSingleError - excepciones no mapeadas devuelven INTERNAL_SERVER_ERROR")
    void shouldReturnInternalErrorForUnknownException() {
        GraphQLError error = handler.resolveToSingleError(new RuntimeException("boom"), env);

        assertThat(error).isNotNull();
        assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        assertThat(error.getExtensions()).containsEntry("code", "INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("resolveToSingleError - unwrap de CompletionException para alcanzar la causa real")
    void shouldUnwrapCompletionException() {
        Throwable cause = new ValidationException("root cause");
        Throwable wrapped = new CompletionException(cause);

        GraphQLError error = handler.resolveToSingleError(wrapped, env);

        assertThat(error.getExtensions()).containsEntry("code", "USER_CONSTRAINTS_NOT_ALLOWED");
    }

    @Test
    @DisplayName("resolveToSingleError - si MessageResolver no esta disponible usa el codigo crudo")
    void shouldUseRawCodeWhenMessageResolverNotAvailable() {
        when(messageResolverProvider.getIfAvailable()).thenReturn(null);

        GraphQLError error = handler.resolveToSingleError(
                new RentFailedException("x"), env);

        assertThat(error.getMessage()).isEqualTo("error.rental.failed");
    }

    @Test
    @DisplayName("resolveToSingleError - extensions incluye timestamp ISO-8601")
    void shouldIncludeTimestampInExtensions() {
        GraphQLError error = handler.resolveToSingleError(new AccessDeniedException("x"), env);

        Object timestamp = error.getExtensions().get("timestamp");
        assertThat(timestamp).isNotNull();
        assertThat(timestamp.toString()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
    }

    @Test
    @DisplayName("resolveToSingleError - extensions incluye el codigo de error")
    void shouldBuildErrorWithEnvironment() {
        GraphQLError error = handler.resolveToSingleError(new AccessDeniedException("x"), env);

        assertThat(error).isNotNull();
        assertThat(error.getExtensions()).containsKey("code");
    }
}
