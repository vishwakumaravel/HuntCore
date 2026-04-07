package com.huntcore.backendapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IngestApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-HuntCore-Api-Key";

    private final IngestSecurityProperties ingestSecurityProperties;

    public IngestApiKeyFilter(IngestSecurityProperties ingestSecurityProperties) {
        this.ingestSecurityProperties = ingestSecurityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (!requiresApiKey(request) || ingestSecurityProperties.getApiKey().isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String actualApiKey = request.getHeader(API_KEY_HEADER);
        if (ingestSecurityProperties.getApiKey().equals(actualApiKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = "{\"status\":401,\"error\":\"Unauthorized.\"}".getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(body);
    }

    private boolean requiresApiKey(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/v1/matches".equals(path)) {
            return true;
        }

        return "PUT".equalsIgnoreCase(request.getMethod())
            && path.startsWith("/api/v1/servers/")
            && path.endsWith("/heartbeat");
    }
}
