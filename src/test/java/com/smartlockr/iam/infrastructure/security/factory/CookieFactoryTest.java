package com.smartlockr.iam.infrastructure.security.factory;

import com.smartlockr.shared.properties.CookieProperties;
import com.smartlockr.iam.domain.enums.SameSite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookieFactoryTest {

    @Mock
    CookieProperties cookieProperties;

    @InjectMocks
    private CookieFactory cookieFactory;

    @BeforeEach
    void init() {
        when(cookieProperties.secure()).thenReturn(true);
        when(cookieProperties.sameSite()).thenReturn(SameSite.STRICT);
    }

    @Nested
    @DisplayName("Use Case: Check whether is creating cookies or not")
    class CookieTest {
        @DisplayName("Just checks if creates the cookie")
        @Test
        void cookie_creation() {
            var cookie = cookieFactory.create("AnyValue", "fake_token", Duration.ofSeconds(3600));
            assertEquals(3600, cookie.getMaxAge().getSeconds(), "Max Age should be 3600");
        }

        @DisplayName("Checks if it cleans the cookie")
        @Test
        void cookie_cleaning() {
            var cookie = cookieFactory.clean("AnyValue");

            assertEquals(0, cookie.getMaxAge().getSeconds(), "Max Age should be zero");


        }
    }
}
