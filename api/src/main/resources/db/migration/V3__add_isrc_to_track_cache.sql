-- Add ISRC column for Spotify track cache entries

ALTER TABLE track_cache
    ADD COLUMN IF NOT EXISTS isrc TEXT;

CREATE INDEX IF NOT EXISTS idx_track_cache_isrc ON track_cache (isrc);
