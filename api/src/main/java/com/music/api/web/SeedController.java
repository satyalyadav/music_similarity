package com.music.api.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.music.api.seeds.SeedService;
import com.music.api.seeds.SeedsResponse;

@RestController
public class SeedController {

    private final SeedService seedService;

    public SeedController(SeedService seedService) {
        this.seedService = seedService;
    }

    @GetMapping("/me/seeds")
    public ResponseEntity<SeedsResponse> seeds(
        @RequestParam("userId") UUID userId,
        @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        SeedsResponse response = new SeedsResponse(seedService.getSeedTracks(userId, limit));
        return ResponseEntity.ok(response);
    }
}
