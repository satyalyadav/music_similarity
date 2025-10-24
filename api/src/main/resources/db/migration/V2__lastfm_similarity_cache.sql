-- Cache to store Last.fm similarity responses for reuse

CREATE TABLE lastfm_similarity_cache (
    seed_type TEXT NOT NULL,
    seed_key TEXT NOT NULL,
    response_json JSONB NOT NULL,
    cached_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (seed_type, seed_key)
);

CREATE INDEX idx_lastfm_similarity_cache_cached_at
    ON lastfm_similarity_cache (cached_at);
