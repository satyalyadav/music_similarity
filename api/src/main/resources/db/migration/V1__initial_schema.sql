-- Initial schema for music similarity service

CREATE TABLE user_auth (
    user_id UUID PRIMARY KEY,
    spotify_id TEXT NOT NULL UNIQUE,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    scopes TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE track_cache (
    spotify_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT NOT NULL,
    popularity INTEGER NOT NULL CHECK (popularity BETWEEN 0 AND 100),
    image_url TEXT,
    cached_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE id_map (
    source TEXT NOT NULL,
    source_key TEXT NOT NULL,
    spotify_id TEXT NOT NULL,
    confidence NUMERIC,
    cached_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (source, source_key)
);

CREATE INDEX idx_id_map_spotify_id ON id_map (spotify_id);

CREATE TABLE artist_tags (
    artist_name TEXT NOT NULL,
    tags_json JSONB NOT NULL,
    cached_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source TEXT NOT NULL,
    PRIMARY KEY (artist_name, source)
);

CREATE TABLE feedback (
    user_id UUID NOT NULL,
    spotify_id TEXT NOT NULL,
    label SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, spotify_id)
);
