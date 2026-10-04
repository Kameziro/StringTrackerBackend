package br.com.stringtracker.repository;

import br.com.stringtracker.dto.CreateGroupRequest;
import br.com.stringtracker.dto.RegisterDeviceTokenRequest;
import br.com.stringtracker.dto.UpdateAvailabilityRequest;
import br.com.stringtracker.model.AvailabilitySlot;
import br.com.stringtracker.model.BaseEntity;
import br.com.stringtracker.model.City;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.DeviceToken;
import br.com.stringtracker.model.GameInterest;
import br.com.stringtracker.model.GroupMember;
import br.com.stringtracker.model.OpenGame;
import br.com.stringtracker.model.PlayerGroup;
import br.com.stringtracker.model.User;
import br.com.stringtracker.service.GroupService;
import br.com.stringtracker.service.OpenGameService;
import br.com.stringtracker.service.ProfileService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Linhas com {@code active = false} (soft-delete via {@link BaseEntity#markExcluded()})
 * não podem aparecer em listagens nem em buscas.
 */
@QuarkusTest
class SoftDeletedRowsTest {

    private static final long CITY_ID = 5565L;

    @Inject
    EntityManager em;

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    PlayerGroupRepository playerGroupRepository;

    @Inject
    GroupMemberRepository groupMemberRepository;

    @Inject
    OpenGameRepository openGameRepository;

    @Inject
    GameInterestRepository gameInterestRepository;

    @Inject
    DeviceTokenRepository deviceTokenRepository;

    @Inject
    AvailabilitySlotRepository availabilitySlotRepository;

    @Inject
    OpenGameService openGameService;

    @Inject
    GroupService groupService;

    @Inject
    ProfileService profileService;

    @Test
    void userListings_hideDeactivatedUsers() {
        User active = newUser(3);
        User deactivated = newUser(3);
        persist(active, deactivated);
        softDelete(deactivated);

        List<Long> available = ids(userRepository.findAvailableTodayByCategoryAndCity(3, CITY_ID));
        assertTrue(available.contains(active.getId()));
        assertFalse(available.contains(deactivated.getId()));

        List<Long> notified = ids(userRepository.findByCategoryAndCity(3, CITY_ID));
        assertTrue(notified.contains(active.getId()));
        assertFalse(notified.contains(deactivated.getId()));
    }

    @Test
    void clubLookups_hideDeletedClubs() {
        User user = newUser(4);
        Club active = Club.create(unique("club"));
        Club deleted = Club.create(unique("club"));
        persist(user, active, deleted);
        softDelete(deleted);

        List<Long> listed = ids(clubRepository.listAllActive());
        assertTrue(listed.contains(active.getId()));
        assertFalse(listed.contains(deleted.getId()));
        assertTrue(clubRepository.findByNameIgnoreCase(deleted.getName()).isEmpty());
        assertTrue(clubRepository.findActiveById(active.getId()).isPresent());
        assertTrue(clubRepository.findActiveById(deleted.getId()).isEmpty());

        UpdateAvailabilityRequest atDeletedClub = new UpdateAvailabilityRequest(List.of(
                new UpdateAvailabilityRequest.AvailabilitySlotRequest(
                        1, LocalTime.of(18, 0), LocalTime.of(20, 0), deleted.getId())
        ));
        assertThrows(NotFoundException.class, () -> profileService.replaceAvailability(user, atDeletedClub));
    }

    @Test
    void groupLookups_hideDeletedGroupsAndMemberships() {
        User creator = newUser(5);
        User stays = newUser(5);
        User left = newUser(5);
        PlayerGroup group = PlayerGroup.create(unique("group"), creator);
        PlayerGroup deletedGroup = PlayerGroup.create(unique("group"), creator);
        GroupMember staysMembership = GroupMember.create(group, stays);
        GroupMember leftMembership = GroupMember.create(group, left);
        GroupMember membershipOfDeletedGroup = GroupMember.create(deletedGroup, stays);
        persist(creator, stays, left, group, deletedGroup, staysMembership, leftMembership, membershipOfDeletedGroup);
        softDelete(deletedGroup, leftMembership);

        List<Long> listed = ids(playerGroupRepository.listAllOrdered());
        assertTrue(listed.contains(group.getId()));
        assertFalse(listed.contains(deletedGroup.getId()));
        assertTrue(playerGroupRepository.findByNameIgnoreCase(deletedGroup.getName()).isEmpty());
        assertThrows(NotFoundException.class, () -> groupService.requireGroup(deletedGroup.getId()));

        assertTrue(groupMemberRepository.isMember(group, stays));
        assertFalse(groupMemberRepository.isMember(group, left));
        assertTrue(groupMemberRepository.findByGroupAndUser(group, left).isEmpty());
        assertTrue(groupMemberRepository.findByGroupIdAndUserId(group.getId(), left.getId()).isEmpty());
        assertEquals(List.of(staysMembership.getId()), ids(groupMemberRepository.findByGroup(group)));
        assertEquals(1, groupMemberRepository.countByGroup(group));
        assertEquals(List.of(stays.getId()), groupMemberRepository.findMemberUserIds(group));

        List<Long> groupsOfStays = groupMemberRepository.findByUser(stays).stream()
                .map(m -> m.getGroup().getId())
                .toList();
        assertEquals(List.of(group.getId()), groupsOfStays);
        assertTrue(groupMemberRepository.findByUser(left).isEmpty());
    }

    @Test
    void openGameLookups_hideDeletedGamesInterestsAndMemberships() {
        User organizer = newUser(6);
        User withdrawn = newUser(6);
        User interested = newUser(6);
        Club club = Club.create(unique("club"));
        PlayerGroup group = PlayerGroup.create(unique("group"), organizer);
        GroupMember withdrawnMembership = GroupMember.create(group, withdrawn);
        OpenGame open = newGame(organizer, club, null);
        OpenGame deleted = newGame(organizer, club, null);
        OpenGame groupGame = newGame(organizer, club, group);
        GameInterest withdrawnInterest = GameInterest.create(open, withdrawn);
        GameInterest activeInterest = GameInterest.create(open, interested);
        persist(organizer, withdrawn, interested, club, group, withdrawnMembership,
                open, deleted, groupGame, withdrawnInterest, activeInterest);
        softDelete(deleted, withdrawnMembership, withdrawnInterest);

        List<Long> visible = ids(openGameRepository.findOpenVisibleTo(withdrawn, 6, CITY_ID));
        assertTrue(visible.contains(open.getId()));
        assertFalse(visible.contains(deleted.getId()));
        assertFalse(visible.contains(groupGame.getId()));

        List<Long> organized = ids(openGameRepository.findForUser(organizer));
        assertTrue(organized.contains(open.getId()));
        assertFalse(organized.contains(deleted.getId()));
        assertEquals(List.of(), ids(openGameRepository.findForUser(withdrawn)));
        assertEquals(List.of(open.getId()), ids(openGameRepository.findForUser(interested)));
        assertThrows(NotFoundException.class, () -> openGameService.get(deleted.getId()));

        assertTrue(gameInterestRepository.findByGameAndUser(open, withdrawn).isEmpty());
        assertEquals(List.of(activeInterest.getId()), ids(gameInterestRepository.findByGame(open)));
        assertEquals(1, gameInterestRepository.countInterested(open));
        assertEquals(List.of(activeInterest.getId()), ids(gameInterestRepository.findInterestedOrdered(open)));
    }

    @Test
    void deviceTokenAndAvailabilityLookups_hideDeletedRows() {
        User user = newUser(7);
        DeviceToken activeToken = DeviceToken.create(user, unique("ExponentPushToken"), "ios");
        DeviceToken deletedToken = DeviceToken.create(user, unique("ExponentPushToken"), "ios");
        AvailabilitySlot activeSlot = AvailabilitySlot.create(user, 1, LocalTime.of(8, 0), LocalTime.of(10, 0), null);
        AvailabilitySlot deletedSlot = AvailabilitySlot.create(user, 2, LocalTime.of(8, 0), LocalTime.of(10, 0), null);
        persist(user, activeToken, deletedToken, activeSlot, deletedSlot);
        softDelete(deletedToken, deletedSlot);

        assertTrue(deviceTokenRepository.findByToken(deletedToken.getExpoPushToken()).isEmpty());
        assertEquals(List.of(activeToken.getId()), ids(deviceTokenRepository.findByUser(user)));
        assertEquals(List.of(activeToken.getId()), ids(deviceTokenRepository.findByUserIds(List.of(user.getId()))));
        assertEquals(List.of(activeSlot.getId()), ids(availabilitySlotRepository.findByUser(user)));
    }

    @Test
    void namesOfDeletedClubsAndGroups_canBeReused() {
        User user = newUser(4);
        Club deletedClub = Club.create(unique("club"));
        PlayerGroup deletedGroup = PlayerGroup.create(unique("group"), user);
        persist(user, deletedClub, deletedGroup);
        softDelete(deletedClub, deletedGroup);

        Club recreatedClub = QuarkusTransaction.requiringNew()
                .call(() -> clubRepository.findOrCreateByName(deletedClub.getName()));
        assertNotEquals(deletedClub.getId(), recreatedClub.getId());

        Long recreatedGroupId = groupService.create(user, new CreateGroupRequest(deletedGroup.getName())).getId();
        assertNotEquals(deletedGroup.getId(), recreatedGroupId);
    }

    @Test
    void deletedMembershipsInterestsAndTokens_areRecreatedOnTheNextRequest() {
        User organizer = newUser(8);
        User player = newUser(8);
        Club club = Club.create(unique("club"));
        PlayerGroup group = PlayerGroup.create(unique("group"), organizer);
        GroupMember oldMembership = GroupMember.create(group, player);
        OpenGame game = newGame(organizer, club, null);
        GameInterest oldInterest = GameInterest.create(game, player);
        DeviceToken oldToken = DeviceToken.create(player, unique("ExponentPushToken"), "ios");
        persist(organizer, player, club, group, oldMembership, game, oldInterest, oldToken);
        softDelete(oldMembership, oldInterest, oldToken);

        groupService.join(player, group.getId());
        GroupMember membership = groupMemberRepository.findByGroupAndUser(group, player).orElseThrow();
        assertNotEquals(oldMembership.getId(), membership.getId());

        openGameService.expressInterest(player, game.getId());
        GameInterest interest = gameInterestRepository.findByGameAndUser(game, player).orElseThrow();
        assertNotEquals(oldInterest.getId(), interest.getId());

        profileService.registerDeviceToken(player, new RegisterDeviceTokenRequest(oldToken.getExpoPushToken(), "ios"));
        DeviceToken token = deviceTokenRepository.findByToken(oldToken.getExpoPushToken()).orElseThrow();
        assertNotEquals(oldToken.getId(), token.getId());
    }

    private User newUser(int category) {
        String keycloakId = unique("kc");
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setEmail(keycloakId + "@example.com");
        user.setName("Jogador " + keycloakId);
        user.setCategory(category);
        user.setCity(em.getReference(City.class, CITY_ID));
        user.setAvailableToday(true);
        return user;
    }

    private static OpenGame newGame(User organizer, Club club, PlayerGroup group) {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        return OpenGame.create(organizer, club, start, start.plus(2, ChronoUnit.HOURS),
                organizer.getCategory(), 4, group);
    }

    private void persist(Object... entities) {
        QuarkusTransaction.requiringNew().run(() -> {
            for (Object entity : entities) {
                em.persist(entity);
            }
        });
    }

    private void softDelete(BaseEntity... entities) {
        QuarkusTransaction.requiringNew().run(() -> {
            for (BaseEntity entity : entities) {
                em.find(entity.getClass(), entity.getId()).markExcluded();
            }
        });
    }

    private static List<Long> ids(List<? extends BaseEntity> entities) {
        return entities.stream().map(BaseEntity::getId).toList();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
