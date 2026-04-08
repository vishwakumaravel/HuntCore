package com.huntcore.backendapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfigurationWarning {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfigurationWarning.class);

    @Bean
    ApplicationRunner warnAboutRiskySecurityDefaults(
        IngestSecurityProperties ingestSecurityProperties,
        PublicApiProperties publicApiProperties,
        @Value("${spring.datasource.username:}") String datasourceUsername,
        @Value("${spring.datasource.password:}") String datasourcePassword
    ) {
        return args -> {
            if (ingestSecurityProperties.getApiKey().isBlank()) {
                LOGGER.warn(
                    "HUNTCORE_INGEST_API_KEY is blank. The write ingest routes are currently unauthenticated."
                );
            }

            if ("*".equals(publicApiProperties.getAllowedOrigin())) {
                LOGGER.warn(
                    "HUNTCORE_PUBLIC_ALLOWED_ORIGIN is '*'. Public dashboard routes allow any browser origin."
                );
            }

            if ("huntcore".equals(datasourceUsername) && "huntcore".equals(datasourcePassword)) {
                LOGGER.warn(
                    "Spring datasource is still using the default huntcore/huntcore credentials. Change them before public hosting."
                );
            }
        };
    }
}
