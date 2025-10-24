package com.music.api.web;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<AuthCallbackResponse> callback(
        @RequestParam("code") String code,
        @RequestParam("state") String state
    ) {
        AuthResult result = spotifyAuthService.completeAuthorization(code, state);
        AuthCallbackResponse body = new AuthCallbackResponse(
            result.userId(),
            result.profile().id(),
            result.profile().displayName(),
            result.redirectUri()
        );
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }
}
