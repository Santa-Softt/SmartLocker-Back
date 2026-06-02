package com.smartlockr.iam.infrastructure.security.error.exception;

import lombok.experimental.StandardException;
import org.springframework.security.core.AuthenticationException;

@StandardException
public class GoogleVerifierException extends AuthenticationException {
}
