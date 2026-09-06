package com.custos.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

@Slf4j
@Configuration
@Getter
@Setter
public class AwsSesConfig {

    @Value("${custos.aws.ses.enabled:false}")
    private boolean enabled;

    @Value("${custos.aws.ses.region:ap-southeast-1}")
    private String region;

    @Value("${custos.aws.ses.sender-email:alerts@custos.platform}")
    private String senderEmail;

    @Value("${custos.aws.ses.access-key:}")
    private String accessKey;

    @Value("${custos.aws.ses.secret-key:}")
    private String secretKey;

    @Value("${custos.aws.ses.rate-limit-per-second:14}")
    private int rateLimitPerSecond;

    @Bean
    public SesClient sesClient() {
        if (!enabled || accessKey == null || accessKey.trim().isEmpty() || secretKey == null || secretKey.trim().isEmpty()) {
            log.info("AWS SES Client configured in MOCK / DEVELOPMENT mode (enabled: {})", enabled);
            return null;
        }

        try {
            log.info("Initializing production AWS SES Client for region: {}", region);
            return SesClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to initialize AWS SES Client, falling back to mock: {}", e.getMessage());
            return null;
        }
    }
}
