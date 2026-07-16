package br.com.stringtracker.service;

import br.com.stringtracker.dto.AvailabilitySlotResponse;
import br.com.stringtracker.dto.ProfileResponse;
import br.com.stringtracker.dto.RegisterDeviceTokenRequest;
import br.com.stringtracker.dto.UpdateAvailabilityRequest;
import br.com.stringtracker.dto.UpdateProfileRequest;
import br.com.stringtracker.model.AvailabilitySlot;
import br.com.stringtracker.model.City;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.DeviceToken;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.AvailabilitySlotRepository;
import br.com.stringtracker.repository.CityRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.DeviceTokenRepository;
import br.com.stringtracker.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ProfileService {

    @Inject
    UserRepository userRepository;

    @Inject
    AvailabilitySlotRepository availabilitySlotRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    CityRepository cityRepository;

    @Inject
    DeviceTokenRepository deviceTokenRepository;

    @Transactional
    public ProfileResponse updateProfile(User user, UpdateProfileRequest request) {
        User managed = userRepository.findByIdOptional(user.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        City city = cityRepository.findByIdOptional(request.cityId())
                .orElseThrow(() -> new NotFoundException("Cidade não encontrada"));
        managed.setName(request.name().trim());
        managed.setCategory(request.category());
        managed.setCity(city);
        boolean available = Boolean.TRUE.equals(request.availableToday());
        managed.setAvailableToday(available);
        managed.setAvailableTodayAt(available ? Instant.now() : null);
        return ProfileResponse.from(managed);
    }

    public ProfileResponse getProfile(User user) {
        return ProfileResponse.from(user);
    }

    @Transactional
    public List<AvailabilitySlotResponse> replaceAvailability(User user, UpdateAvailabilityRequest request) {
        User managed = userRepository.findByIdOptional(user.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        availabilitySlotRepository.deleteByUser(managed);
        for (UpdateAvailabilityRequest.AvailabilitySlotRequest slotReq : request.slots()) {
            if (!slotReq.endTime().isAfter(slotReq.startTime())) {
                throw new BadRequestException("endTime must be after startTime");
            }
            Club club = null;
            if (slotReq.clubId() != null) {
                club = clubRepository.findByIdOptional(slotReq.clubId())
                        .orElseThrow(() -> new NotFoundException("Club not found"));
            }
            AvailabilitySlot slot = AvailabilitySlot.create(
                    managed,
                    slotReq.dayOfWeek(),
                    slotReq.startTime(),
                    slotReq.endTime(),
                    club
            );
            availabilitySlotRepository.persist(slot);
        }
        return listAvailability(managed);
    }

    public List<AvailabilitySlotResponse> listAvailability(User user) {
        return availabilitySlotRepository.findByUser(user).stream()
                .map(AvailabilitySlotResponse::from)
                .toList();
    }

    @Transactional
    public void registerDeviceToken(User user, RegisterDeviceTokenRequest request) {
        User managed = userRepository.findByIdOptional(user.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        String token = request.expoPushToken().trim();
        DeviceToken existing = deviceTokenRepository.findByToken(token).orElse(null);
        if (existing != null) {
            existing.setUser(managed);
            existing.setPlatform(request.platform().trim());
            return;
        }
        deviceTokenRepository.persist(DeviceToken.create(managed, token, request.platform().trim()));
    }
}
