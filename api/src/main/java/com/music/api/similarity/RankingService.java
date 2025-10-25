package com.music.api.similarity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.music.api.auth.UserAuth;
import com.music.api.similarity.CandidateMappingService.MappedTrack;
import com.music.api.similarity.TrackCacheRepository.TrackCacheEntry;

@Service
public class RankingService {

    private static final double TAG_WEIGHT = 0.2;

    private final ArtistTagService artistTagService;
    private final TrackCacheService trackCacheService;

    public RankingService(
        ArtistTagService artistTagService,
        TrackCacheService trackCacheService
    ) {
        this.artistTagService = artistTagService;
        this.trackCacheService = trackCacheService;
    }

    public List<RankedTrack> rank(UserAuth userAuth, String seedArtist, List<MappedTrack> candidates) {
        Set<String> seedTags = artistTagService.getTags(seedArtist);

        return candidates.stream()
            .map(candidate -> mapToRanked(userAuth, candidate, seedTags))
            .sorted(Comparator
                .comparing(RankedTrack::score).reversed()
                .thenComparing(RankedTrack::popularity, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedTrack::name))
            .collect(Collectors.toList());
    }

    private RankedTrack mapToRanked(UserAuth userAuth, MappedTrack candidate, Set<String> seedTags) {
        double baseScore = candidate.source().matchScore();
        if (baseScore <= 0) {
            baseScore = 0.4;
        }

        Set<String> candidateTags = artistTagService.getTags(candidate.source().artist());
        double tagOverlap = artistTagService.jaccard(seedTags, candidateTags);
        double compositeScore = baseScore + (TAG_WEIGHT * tagOverlap);

        Integer popularity = resolvePopularity(userAuth, candidate.spotifyId())
            .map(TrackCacheEntry::popularity)
            .orElse(null);

        return new RankedTrack(
            candidate.source().name(),
            candidate.source().artist(),
            candidate.spotifyId(),
            compositeScore,
            candidate.source().matchScore(),
            tagOverlap,
            candidate.confidence(),
            popularity,
            candidate.cached(),
            candidate.source().url(),
            candidate.source().imageUrl()
        );
    }

    private Optional<TrackCacheEntry> resolvePopularity(UserAuth userAuth, String spotifyId) {
        if (spotifyId == null) {
            return Optional.empty();
        }
        return trackCacheService.getTrack(userAuth, spotifyId);
    }

    public record RankedTrack(
        String name,
        String artist,
        String spotifyId,
        double score,
        double rawMatch,
        double tagOverlap,
        double confidence,
        Integer popularity,
        boolean cached,
        String lastFmUrl,
        String imageUrl
    ) {}
}
