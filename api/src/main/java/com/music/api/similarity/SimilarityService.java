package com.music.api.similarity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.api.similarity.LastFmClient.LastFmArtist;
import com.music.api.similarity.LastFmClient.LastFmTrack;

@Service
public class SimilarityService {

    private static final Logger log = LoggerFactory.getLogger(SimilarityService.class);
    private static final int TRACK_LIMIT = 100;
    private static final int ARTIST_LIMIT = 15;
    private static final int TOP_TRACKS_PER_ARTIST = 5;

    private static final TypeReference<SimilarityResult> RESULT_TYPE = new TypeReference<>() {};

    private final LastFmClient lastFmClient;
    private final LastFmCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;
    private final LastFmProperties properties;

    public SimilarityService(
        LastFmClient lastFmClient,
        LastFmCacheRepository cacheRepository,
        ObjectMapper objectMapper,
        LastFmProperties properties
    ) {
        this.lastFmClient = lastFmClient;
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public SimilarityResult getSimilarTracks(String seedArtist, String seedTrack) {
        String normalizedKey = SimilarityKeys.normalize(seedArtist, seedTrack);
        Optional<SimilarityResult> cached = loadFromCache("track", normalizedKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Primary strategy: track.getSimilar
        List<LastFmTrack> similarTracks = lastFmClient.getSimilarTracks(seedArtist, seedTrack, TRACK_LIMIT);
        if (!similarTracks.isEmpty()) {
            return cacheAndReturn("track", normalizedKey, new SimilarityResult("track.getSimilar", normalizeDistinct(similarTracks)));
        }

        // Fallback 1: similar artists and their top tracks
        List<LastFmTrack> fromSimilarArtists = gatherFromSimilarArtists(seedArtist);
        if (!fromSimilarArtists.isEmpty()) {
            return cacheAndReturn("track", normalizedKey, new SimilarityResult("artist.getSimilar", fromSimilarArtists));
        }

        // Fallback 2: artist top tracks
        List<LastFmTrack> artistTopTracks = normalizeDistinct(lastFmClient.getArtistTopTracks(seedArtist, TRACK_LIMIT));
        if (!artistTopTracks.isEmpty()) {
            return cacheAndReturn("track", normalizedKey, new SimilarityResult("artist.getTopTracks", artistTopTracks));
        }

        // Fallback 3: geo top tracks
        List<LastFmTrack> geoTracks = normalizeDistinct(lastFmClient.getGeoTopTracks(properties.getDefaultCountry(), TRACK_LIMIT));
        return cacheAndReturn("track", normalizedKey, new SimilarityResult("geo.getTopTracks", geoTracks));
    }

    private List<LastFmTrack> gatherFromSimilarArtists(String seedArtist) {
        List<LastFmArtist> similarArtists = lastFmClient.getSimilarArtists(seedArtist, ARTIST_LIMIT);
        if (similarArtists.isEmpty()) {
            return List.of();
        }

        List<LastFmTrack> collected = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (LastFmArtist artist : similarArtists) {
            List<LastFmTrack> topTracks = lastFmClient.getArtistTopTracks(artist.name(), TOP_TRACKS_PER_ARTIST);
            for (LastFmTrack track : topTracks) {
                String key = SimilarityKeys.normalize(track.artist(), track.name());
                if (seen.add(key)) {
                    collected.add(track);
                }
            }
        }
        return collected;
    }

    private List<LastFmTrack> normalizeDistinct(List<LastFmTrack> tracks) {
        Set<String> seen = new LinkedHashSet<>();
        List<LastFmTrack> cleaned = new ArrayList<>();
        for (LastFmTrack track : tracks) {
            if (track == null || track.name() == null || track.artist() == null) {
                continue;
            }
            String key = SimilarityKeys.normalize(track.artist(), track.name());
            if (seen.add(key)) {
                cleaned.add(track);
            }
        }
        return cleaned;
    }

    private SimilarityResult cacheAndReturn(String seedType, String seedKey, SimilarityResult result) {
        try {
            cacheRepository.upsert(seedType, seedKey, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to cache similarity result for {}:{} - {}", seedType, seedKey, ex.getMessage());
        }
        return result;
    }

    private Optional<SimilarityResult> loadFromCache(String seedType, String seedKey) {
        return cacheRepository.findFreshResponse(seedType, seedKey)
            .flatMap(json -> {
                try {
                    return Optional.ofNullable(objectMapper.readValue(json, RESULT_TYPE));
                } catch (JsonProcessingException ex) {
                    log.warn("Failed to deserialize cached similarity result for {}:{} - {}", seedType, seedKey, ex.getMessage());
                    return Optional.empty();
                }
            });
    }

    public record SimilarityResult(
        String strategy,
        List<LastFmTrack> tracks
    ) {}
}
