package com.music.api.similarity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CacheCleanupJob.class);

    private final TrackCacheRepository trackCacheRepository;
    private final IdMapRepository idMapRepository;
    private final ArtistTagsRepository artistTagsRepository;

    public CacheCleanupJob(
        TrackCacheRepository trackCacheRepository,
        IdMapRepository idMapRepository,
        ArtistTagsRepository artistTagsRepository
    ) {
        this.trackCacheRepository = trackCacheRepository;
        this.idMapRepository = idMapRepository;
        this.artistTagsRepository = artistTagsRepository;
    }

    // Run daily at 03:00 server time
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeStaleCacheRows() {
        int trackDeleted = trackCacheRepository.purgeExpired();
        int idMapDeleted = idMapRepository.purgeExpired();
        int tagsDeleted = artistTagsRepository.purgeExpired();
        if (trackDeleted + idMapDeleted + tagsDeleted > 0) {
            log.info("Cache cleanup removed entries - track_cache: {}, id_map: {}, artist_tags: {}", trackDeleted, idMapDeleted, tagsDeleted);
        }
    }
}






