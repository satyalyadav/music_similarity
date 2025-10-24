package com.music.api.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class OAuthStateStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, OAuthState> cache = new ConcurrentHashMap<>();

    public OAuthState create(String redirectUri) {
        Assert.hasText(redirectUri, "redirectUri must not be blank");
        removeExpiredEntries();
        String state = generateStateToken();
        OAuthState entry = new OAuthState(state, redirectUri, Instant.now().plus(DEFAULT_TTL));
        cache.put(state, entry);
        return entry;
    }

    public Optional<OAuthState> consume(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        OAuthState entry = cache.remove(state);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private String generateStateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void removeExpiredEntries() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record OAuthState(String value, String redirectUri, Instant expiresAt) {}
}
