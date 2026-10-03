package com.custos.modules.docker.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Docker Engine API client. The connection is lazy, so a missing docker.sock does not
 * break startup; the service reports it as "unavailable" when it is first used.
 */
@Configuration
@ConditionalOnProperty(prefix = "custos.docker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DockerConfig {

    @Bean(destroyMethod = "close")
    public DockerClient dockerClient(@Value("${custos.docker.host:unix:///var/run/docker.sock}") String dockerHost) {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofMinutes(10))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
