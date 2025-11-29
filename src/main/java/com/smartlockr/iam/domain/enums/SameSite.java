package com.smartlockr.iam.domain.enums;

public enum SameSite {
    STRICT("Strict"),
    LAX("Lax"),
    NONE("None");

    private final String attribute;

    SameSite(String attribute) {
        this.attribute = attribute;
    }

    public String attribute() {
        return attribute;
    }
}
