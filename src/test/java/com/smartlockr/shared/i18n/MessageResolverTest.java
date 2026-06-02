package com.smartlockr.shared.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

class MessageResolverTest {

    @Test
    @DisplayName("resolve - delegates to MessageSource using provided locale")
    void shouldResolveWithProvidedLocale() {
        MessageSource messageSource = mock(MessageSource.class);
        MessageResolver resolver = new MessageResolver(messageSource);
        given(messageSource.getMessage("error.code", new Object[]{"A"}, "error.code", Locale.ENGLISH))
                .willReturn("Resolved");

        assertThat(resolver.resolve("error.code", Locale.ENGLISH, "A")).isEqualTo("Resolved");
    }

    @Test
    @DisplayName("resolve - uses default locale when locale is null")
    void shouldUseDefaultLocaleWhenLocaleIsNull() {
        MessageSource messageSource = mock(MessageSource.class);
        MessageResolver resolver = new MessageResolver(messageSource);
        Locale defaultLocale = Locale.getDefault();
        given(messageSource.getMessage(eq("missing.code"), any(Object[].class), eq("missing.code"), eq(defaultLocale)))
                .willReturn("missing.code");

        assertThat(resolver.resolve("missing.code", null)).isEqualTo("missing.code");
    }
}
