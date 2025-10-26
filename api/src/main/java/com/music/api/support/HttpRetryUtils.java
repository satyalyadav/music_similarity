package com.music.api.support;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public final class HttpRetryUtils {

    private static final Duration MIN_BACKOFF = Duration.ofMillis(200);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(8);

    private HttpRetryUtils() {
    }

    public static boolean isRetryable(Throwable failure) {
        if (failure instanceof WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            return status == 429 || (status >= 500 && status < 600);
        }
        return failure instanceof WebClientRequestException;
    }

    public static long computeDelayMillis(long attempt, Throwable failure) {
        Duration delay;
        if (failure == null) {
            delay = defaultBackoff(attempt);
        } else if (failure instanceof WebClientResponseException ex && ex.getStatusCode().value() == 429) {
            delay = parseRetryAfter(ex.getHeaders())
                .filter(duration -> !duration.isNegative() && !duration.isZero())
                .orElseGet(() -> defaultBackoff(attempt));
        } else if (failure instanceof WebClientResponseException ex && ex.getStatusCode().is5xxServerError()
            || failure instanceof WebClientRequestException) {
            Duration base = defaultBackoff(attempt);
            long jitter = ThreadLocalRandom.current().nextLong(50, 400);
            long totalMillis = Math.min(base.toMillis() + jitter, MAX_BACKOFF.toMillis());
            delay = Duration.ofMillis(totalMillis);
        } else {
            delay = defaultBackoff(attempt);
        }
        return Math.max(1, delay.toMillis());
    }

    private static Duration defaultBackoff(long attempt) {
        double multiplier = Math.pow(2, Math.max(0, attempt - 1));
        long millis = (long) Math.min(MIN_BACKOFF.toMillis() * multiplier, MAX_BACKOFF.toMillis());
        return Duration.ofMillis(millis);
    }

    private static Optional<Duration> parseRetryAfter(HttpHeaders headers) {
        String value = headers.getFirst("Retry-After");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return Optional.of(Duration.ofSeconds(Math.max(1, seconds)));
        } catch (NumberFormatException ignored) {
            try {
                ZonedDateTime dateTime = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
                return Optional.of(Duration.between(ZonedDateTime.now(), dateTime).abs());
            } catch (DateTimeParseException ex) {
                return Optional.empty();
            }
        }
    }

}
