package com.huntcore.backendapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "huntcore.public-api")
public class PublicApiProperties {

    private String allowedOrigin = "*";
    private int staleThresholdSeconds = 90;

    public String getAllowedOrigin() {
        return allowedOrigin;
    }

    public void setAllowedOrigin(String allowedOrigin) {
        this.allowedOrigin = allowedOrigin == null || allowedOrigin.isBlank() ? "*" : allowedOrigin.trim();
    }

    public int getStaleThresholdSeconds() {
        return staleThresholdSeconds;
    }

    public void setStaleThresholdSeconds(int staleThresholdSeconds) {
        this.staleThresholdSeconds = Math.max(5, staleThresholdSeconds);
    }
}
