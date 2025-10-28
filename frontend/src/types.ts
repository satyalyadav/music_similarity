export interface SeedTrackView {
  id: string;
  name: string;
  artist: string;
  album: string;
  imageUrl?: string | null;
  spotifyUrl?: string | null;
}

export interface RecommendationTrackView {
  name: string;
  artist: string;
  spotifyId: string;
  spotifyUrl?: string | null;
  score: number;
  imageUrl?: string | null;
}

export interface RecommendationResponse {
  seed: SeedTrackView;
  strategy: string;
  items: RecommendationTrackView[];
}

export interface PlaylistResponse {
  playlistId: string;
  spotifyUrl?: string;
  tracksAdded: number;
}

export interface SeedsResponse {
  items: SeedTrackView[];
}
