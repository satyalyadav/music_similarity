package com.music.api.feedback;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.music.api.auth.UserAuthRepository;
import com.music.api.spotify.TrackIdNormalizer;

@Service
public class FeedbackService {

    private final UserAuthRepository userAuthRepository;
    private final FeedbackRepository feedbackRepository;

    public FeedbackService(
        UserAuthRepository userAuthRepository,
        FeedbackRepository feedbackRepository
    ) {
        this.userAuthRepository = userAuthRepository;
        this.feedbackRepository = feedbackRepository;
    }

    public FeedbackRecord saveFeedback(UUID userId, String rawTrackId, FeedbackVote vote) {
        userAuthRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User authorization not found"));

        String normalizedTrack = normalize(rawTrackId);
        return feedbackRepository.upsert(userId, normalizedTrack, vote.code());
    }

    private String normalize(String rawTrackId) {
        try {
            return TrackIdNormalizer.normalize(rawTrackId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    public enum FeedbackVote {
        UP((short) 1),
        DOWN((short) -1);

        private final short code;

        FeedbackVote(short code) {
            this.code = code;
        }

        public short code() {
            return code;
        }

        public static FeedbackVote fromLabel(String label) {
            if (label == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback label is required");
            }
            return switch (label.trim().toLowerCase()) {
                case "up", "👍", "plus", "like", "positive" -> UP;
                case "down", "👎", "minus", "dislike", "negative" -> DOWN;
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid feedback label");
            };
        }
    }
}
