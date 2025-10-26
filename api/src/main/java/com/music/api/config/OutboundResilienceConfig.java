package com.music.api.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.music.api.support.HttpRetryUtils;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

@Configuration
public class OutboundResilienceConfig {

    @Bean
    public Retry outboundHttpRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(4)
            .retryOnException(failure -> HttpRetryUtils.isRetryable(failure))
            .intervalBiFunction((attempt, either) -> HttpRetryUtils.computeDelayMillis(attempt, either.getLeft()))
            .build();
        return Retry.of("outbound-http", config);
    }

    @Bean
    public Retry metadataRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .retryOnException(failure -> HttpRetryUtils.isRetryable(failure))
            .intervalBiFunction((attempt, either) -> HttpRetryUtils.computeDelayMillis(attempt, either.getLeft()))
            .build();
        return Retry.of("metadata-http", config);
    }

    @Bean
    public CircuitBreaker lastFmCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50f)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .build();
        return CircuitBreaker.of("lastfm", config);
    }

    @Bean
    public CircuitBreaker musicBrainzCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50f)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .waitDurationInOpenState(Duration.ofSeconds(45))
            .permittedNumberOfCallsInHalfOpenState(5)
            .build();
        return CircuitBreaker.of("musicbrainz", config);
    }
}
