package br.com.stringtracker.dto;

import br.com.stringtracker.model.GameInterest;
import br.com.stringtracker.model.GameInterestStatus;
import br.com.stringtracker.model.OpenGame;
import br.com.stringtracker.model.OpenGameStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenGameResponse {

    private Long id;
    private Long organizerId;
    private String organizerName;
    private Long clubId;
    private String clubName;
    private Long groupId;
    private String groupName;
    private Instant startsAt;
    private Instant endsAt;
    private int category;
    private int capacity;
    private OpenGameStatus status;
    private int interestedCount;
    private List<InterestResponse> interests;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestResponse {

        private Long userId;
        private String userName;
        private GameInterestStatus status;

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
