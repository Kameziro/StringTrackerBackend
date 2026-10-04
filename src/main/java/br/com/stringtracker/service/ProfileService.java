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
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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

    @Inject
    MinioObjectStorage minioObjectStorage;

    @Transactional
    public ProfileResponse updateProfile(User user, UpdateProfileRequest request) {
        User managed = userRepository.findByIdOptional(user.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        City city = cityRepository.findByIdOptional(request.getCityId())
                .orElseThrow(() -> new NotFoundException("Cidade não encontrada"));
        managed.setName(request.getName().trim());
        managed.setCategory(request.getCategory());
        managed.setCity(city);
        boolean available = Boolean.TRUE.equals(request.getAvailableToday());
        managed.setAvailableToday(available);
        managed.setAvailableTodayAt(available ? Instant.now() : null);
        return ProfileResponse.from(managed, minioObjectStorage);
    }

    public ProfileResponse getProfile(User user) {
        return ProfileResponse.from(user, minioObjectStorage);
    }

    @Transactional
    public ProfileResponse uploadAvatar(User user, FileUpload file) {
        if (file == null || file.size() <= 0) {
            throw new BadRequestException("Envie um arquivo de imagem no campo file");
        }
        User managed = userRepository.findByIdOptional(user.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        String previousUrl = managed.getAvatarUrl();
        String previousKey = minioObjectStorage.extractObjectKey(previousUrl);
        String contentType = file.contentType();
        try (InputStream in = Files.newInputStream(file.uploadedFile())) {
            String publicUrl = minioObjectStorage.uploadUserAvatar(
                    managed.getId(),
                    in,
                    file.size(),
                    contentType
            );
            managed.setAvatarUrl(publicUrl);
        } catch (IOException e) {
            throw new BadRequestException("Não foi possível ler a imagem enviada");
        }

        String newKey = minioObjectStorage.extractObjectKey(managed.getAvatarUrl());
        // mesmo object key = overwrite no MinIO — não apagar o arquivo novo
        if (previousKey != null && newKey != null && !previousKey.equals(newKey)) {
            minioObjectStorage.deleteObjectIfPresent(previousUrl);
        }
        return ProfileResponse.from(managed, minioObjectStorage);
    }

    @Transactional
    public List<AvailabilitySlotResponse> replaceAvailability(User user, UpdateAvailabilityRequest request) {
        User managed = userRepository.findByIdOptional(user.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        availabilitySlotRepository.deleteByUser(managed);
        for (UpdateAvailabilityRequest.AvailabilitySlotRequest slotReq : request.getSlots()) {
            if (!slotReq.getEndTime().isAfter(slotReq.getStartTime())) {
                throw new BadRequestException("endTime must be after startTime");
            }
            Club club = null;
            if (slotReq.getClubId() != null) {
                club = clubRepository.findActiveById(slotReq.getClubId())
                        .orElseThrow(() -> new NotFoundException("Club not found"));
            }
            AvailabilitySlot slot = AvailabilitySlot.create(
                    managed,
                    slotReq.getDayOfWeek(),
                    slotReq.getStartTime(),
                    slotReq.getEndTime(),
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
        String token = request.getExpoPushToken().trim();
        DeviceToken existing = deviceTokenRepository.findByToken(token).orElse(null);
        if (existing != null) {
            existing.setUser(managed);
            existing.setPlatform(request.getPlatform().trim());
            return;
        }
        deviceTokenRepository.persist(DeviceToken.create(managed, token, request.getPlatform().trim()));
    }
}
