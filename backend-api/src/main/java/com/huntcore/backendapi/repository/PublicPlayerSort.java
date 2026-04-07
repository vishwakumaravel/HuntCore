package com.huntcore.backendapi.repository;

public enum PublicPlayerSort {
    WINS("wins"),
    KILLS("kills"),
    MATCHES("matches"),
    WIN_RATE("winRate");

    private final String queryValue;

    PublicPlayerSort(String queryValue) {
        this.queryValue = queryValue;
    }

    public String queryValue() {
        return queryValue;
    }

    public static PublicPlayerSort fromQueryValue(String queryValue) {
        if (queryValue == null || queryValue.isBlank()) {
            return WINS;
        }

        for (PublicPlayerSort value : values()) {
            if (value.queryValue.equalsIgnoreCase(queryValue.trim())) {
                return value;
            }
        }

        return WINS;
    }
}
