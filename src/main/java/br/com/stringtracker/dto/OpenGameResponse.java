package br.com.stringtracker.dto;

import br.com.stringtracker.model.GameInterest;
import br.com.stringtracker.model.GameInterestStatus;
import br.com.stringtracker.model.OpenGame;
import br.com.stringtracker.model.OpenGameStatus;

import java.time.Instant;
import java.util.List;

public record OpenGameResponse(
        Long id,
        Long organizerId,
        String organizerName,
        Long clubId,
        String clubName,
        Long groupId,
        String groupName,
        Instant startsAt,
        Instant endsAt,
        int category,
        int capacity,
        OpenGameStatus status,
        int interestedCount,
        List<InterestResponse> interests
) {
    public record InterestResponse(
            Long userId,
            String userName,
            GameInterestStatus status
    ) {
        public static InterestResponse from(GameInterest interest) {
            return new InterestResponse(
                    interest.getUser().getId(),
                    interest.getUser().getName(),
                    interest.getStatus()
            );
        }
    }

    public static OpenGameResponse from(OpenGame game, List<GameInterest> interests) {
        List<InterestResponse> interestResponses = interests.stream()
                .map(InterestResponse::from)
                .toList();
        long interested = interests.stream()
                .filter(i -> i.getStatus() == GameInterestStatus.INTERESTED
                        || i.getStatus() == GameInterestStatus.CONFIRMED)
                .count();
        Long groupId = game.getGroup() != null ? game.getGroup().getId() : null;
        String groupName = game.getGroup() != null ? game.getGroup().getName() : null;
        return new OpenGameResponse(
                game.getId(),
                game.getOrganizer().getId(),
                game.getOrganizer().getName(),
                game.getClub().getId(),
                game.getClub().getName(),
                groupId,
                groupName,
                game.getStartsAt(),
                game.getEndsAt(),
                game.getCategory(),
                game.getCapacity(),
                game.getStatus(),
                (int) interested,
                interestResponses
        );
    }
}
