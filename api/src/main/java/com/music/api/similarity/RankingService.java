package com.music.api.similarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.music.api.auth.UserAuth;
import com.music.api.similarity.CandidateMappingService.MappedTrack;
import com.music.api.similarity.TrackCacheRepository.TrackCacheEntry;

@Service
public class RankingService {

    private static final double BASE_WEIGHT = 0.7;
    private static final double TAG_WEIGHT = 0.2;
    private static final double POPULARITY_WEIGHT = 0.1;

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

        List<CandidateContext> contexts = candidates.stream()
            .map(candidate -> buildContext(userAuth, candidate, seedTags))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(ArrayList::new));

        double meanPopularity = calculateMeanPopularity(contexts);
        double stdPopularity = calculateStdPopularity(contexts, meanPopularity);

        return contexts.stream()
            .map(ctx -> toRankedTrack(ctx, meanPopularity, stdPopularity))
            .sorted(Comparator
                .comparing(RankedTrack::score).reversed()
                .thenComparing(RankedTrack::popularity, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedTrack::name))
            .collect(Collectors.toList());
    }

    private CandidateContext buildContext(UserAuth userAuth, MappedTrack candidate, Set<String> seedTags) {
        double rawMatch = candidate.source().matchScore();
        double baseSimilarity = rawMatch > 0 ? rawMatch : 0.4;

        Set<String> candidateTags = artistTagService.getTags(candidate.source().artist());
        double tagOverlap = artistTagService.jaccard(seedTags, candidateTags);

        Optional<TrackCacheEntry> cachedTrack = resolveTrackDetails(userAuth, candidate.spotifyId());

        Integer popularity = cachedTrack
            .map(TrackCacheEntry::popularity)
            .orElse(null);

        String imageUrl = candidate.spotifyImageUrl();
        if ((imageUrl == null || imageUrl.isBlank()) && cachedTrack.isPresent()) {
            imageUrl = cachedTrack.get().imageUrl();
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = candidate.source().imageUrl();
        }

        return new CandidateContext(
            candidate,
            baseSimilarity,
            rawMatch,
            tagOverlap,
            candidate.confidence(),
            popularity,
            candidate.cached(),
            candidate.source().url(),
            imageUrl
        );
    }

    private Optional<TrackCacheEntry> resolveTrackDetails(UserAuth userAuth, String spotifyId) {
        if (spotifyId == null) {
            return Optional.empty();
        }
        return trackCacheService.getTrack(userAuth, spotifyId);
    }

    private RankedTrack toRankedTrack(CandidateContext context, double meanPopularity, double stdPopularity) {
        double popularityZ = 0.0;
        if (context.popularity() != null && stdPopularity > 0.0) {
            popularityZ = (context.popularity() - meanPopularity) / stdPopularity;
        }

        double compositeScore = BASE_WEIGHT * context.baseSimilarity()
            + TAG_WEIGHT * context.tagOverlap()
            + POPULARITY_WEIGHT * popularityZ;

        return new RankedTrack(
            context.candidate().source().name(),
            context.candidate().source().artist(),
            context.candidate().spotifyId(),
            compositeScore,
            context.rawMatch(),
            context.tagOverlap(),
            context.confidence(),
            context.popularity(),
            context.cached(),
            context.lastFmUrl(),
            context.imageUrl()
        );
    }

    private double calculateMeanPopularity(List<CandidateContext> contexts) {
        return contexts.stream()
            .map(CandidateContext::popularity)
            .filter(Objects::nonNull)
            .mapToDouble(Integer::doubleValue)
            .average()
            .orElse(0.0);
    }

    private double calculateStdPopularity(List<CandidateContext> contexts, double meanPopularity) {
        double variance = contexts.stream()
            .map(CandidateContext::popularity)
            .filter(Objects::nonNull)
            .mapToDouble(pop -> Math.pow(pop - meanPopularity, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
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

    private record CandidateContext(
        MappedTrack candidate,
        double baseSimilarity,
        double rawMatch,
        double tagOverlap,
        double confidence,
        Integer popularity,
        boolean cached,
        String lastFmUrl,
        String imageUrl
    ) {}

}
