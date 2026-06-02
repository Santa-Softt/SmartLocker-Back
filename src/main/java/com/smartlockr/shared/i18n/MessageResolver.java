package com.smartlockr.shared.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;

    public String resolve(String code, Locale locale, Object... args) {
        return messageSource.getMessage(code, args, code, locale == null ? Locale.getDefault() : locale);
    }
}
