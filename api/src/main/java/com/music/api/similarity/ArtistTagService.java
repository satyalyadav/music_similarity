package com.music.api.similarity;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ArtistTagService {

    private static final Logger log = LoggerFactory.getLogger(ArtistTagService.class);

    private final ArtistTagsRepository repository;
    private final MusicBrainzClient musicBrainzClient;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    public ArtistTagService(
        ArtistTagsRepository repository,
        MusicBrainzClient musicBrainzClient,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.musicBrainzClient = musicBrainzClient;
        this.objectMapper = objectMapper;
    }

    public Set<String> getTags(String artistName) {
        if (artistName == null || artistName.isBlank()) {
            return Collections.emptySet();
        }
        return repository.findFreshTags(artistName)
            .flatMap(json -> deserialize(json, artistName))
            .orElseGet(() -> fetchAndCache(artistName));
    }

    private Set<String> fetchAndCache(String artistName) {
        List<String> tags = musicBrainzClient.fetchArtistTags(artistName);
        if (!tags.isEmpty()) {
            try {
                repository.upsert(artistName, objectMapper.writeValueAsString(tags));
            } catch (JsonProcessingException ex) {
                log.warn("Failed to cache tags for {}: {}", artistName, ex.getMessage());
            }
        }
        return tags.stream()
            .map(this::normalize)
            .filter(tag -> !tag.isBlank())
            .collect(Collectors.toCollection(HashSet::new));
    }

    private Optional<Set<String>> deserialize(String json, String artist) {
        try {
            List<String> tags = objectMapper.readValue(json, LIST_TYPE);
            return Optional.of(tags.stream()
                .map(this::normalize)
                .filter(tag -> !tag.isBlank())
                .collect(Collectors.toCollection(HashSet::new)));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialize tags for {}: {}", artist, ex.getMessage());
            return Optional.empty();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
