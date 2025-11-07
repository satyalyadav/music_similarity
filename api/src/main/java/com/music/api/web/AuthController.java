package com.music.api.web;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.music.api.auth.SpotifyAuthService;
import com.music.api.auth.SpotifyAuthService.AuthResult;
import com.music.api.auth.SpotifyAuthService.AuthorizationRequest;
import com.music.api.web.dto.AuthCallbackResponse;

@RestController
public class AuthController {

    private final SpotifyAuthService spotifyAuthService;

    public AuthController(SpotifyAuthService spotifyAuthService) {
        this.spotifyAuthService = spotifyAuthService;
    }

    @GetMapping("/auth/login")
    public ResponseEntity<Void> login(@RequestParam(name = "redirect", required = false) String redirect) {
        AuthorizationRequest request = spotifyAuthService.startAuthorization(Optional.ofNullable(redirect));
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(request.authorizationUri());
        headers.add("Cache-Control", "no-store");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/auth/callback")
    public ResponseEntity<?> callback(
        @RequestParam("code") String code,
        @RequestParam("state") String state
    ) {
        AuthResult result = spotifyAuthService.completeAuthorization(code, state);
        Optional<ResponseEntity<?>> redirectResponse = buildRedirectResponse(result);
        if (redirectResponse.isPresent()) {
            return redirectResponse.get();
        }
        AuthCallbackResponse body = new AuthCallbackResponse(
            result.userId(),
            result.profile().id(),
            result.profile().displayName(),
            result.profile().product(),
            result.profile().imageUrl(),
            result.redirectUri()
        );
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }

    private Optional<ResponseEntity<?>> buildRedirectResponse(AuthResult result) {
        if (result.redirectUri() == null || result.redirectUri().isBlank()) {
            return Optional.empty();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(result.redirectUri())
            .queryParam("userId", result.userId())
            .queryParam("spotifyId", result.profile().id());
        if (result.profile().displayName() != null && !result.profile().displayName().isBlank()) {
            builder.queryParam("displayName", result.profile().displayName());
        }
        if (result.profile().product() != null && !result.profile().product().isBlank()) {
            builder.queryParam("product", result.profile().product());
        }
        if (result.profile().imageUrl() != null && !result.profile().imageUrl().isBlank()) {
            builder.queryParam("imageUrl", result.profile().imageUrl());
        }
        URI redirectLocation = builder.build(false).toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(redirectLocation);
        headers.setCacheControl("no-store");
        return Optional.of(ResponseEntity.status(HttpStatus.FOUND).headers(headers).build());
    }
}
