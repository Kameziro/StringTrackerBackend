package br.com.stringtracker.service;

import br.com.stringtracker.dto.ClubAdminsResponse;
import br.com.stringtracker.dto.ClubPhotoResponse;
import br.com.stringtracker.dto.ClubProfileResponse;
import br.com.stringtracker.dto.InviteResponse;
import br.com.stringtracker.dto.PendingInviteResponse;
import br.com.stringtracker.dto.UpdateClubProfileRequest;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubPhoto;
import br.com.stringtracker.model.InviteKind;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubPhotoRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.InviteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Perfil, imagens e convites do clube. Todo método exige ser admin do clube pedido. */
@ApplicationScoped
public class ClubAdminService {

    private static final int MAX_PHOTOS = 10;

    @Inject
    ClubAccessService access;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubPhotoRepository photoRepository;

    @Inject
    ClubAdminRepository adminRepository;

    @Inject
    InviteRepository inviteRepository;

    @Inject
    InviteService inviteService;

    @Inject
    Clock clock;

    @Inject
    MinioObjectStorage storage;

    @Transactional
    public ClubProfileResponse getProfile(long clubId) {
        access.requireClubAdmin(clubId);
        return toProfile(requireClub(clubId));
    }

    @Transactional
    public ClubProfileResponse updateProfile(long clubId, UpdateClubProfileRequest request) {
        access.requireClubAdmin(clubId);
        Club club = requireClub(clubId);
        String name = request.name().trim();
        clubRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(clubId))
                .ifPresent(other -> {
                    throw new BadRequestException("Já existe um clube com esse nome");
                });
        club.setName(name);
        club.setAddress(blankToNull(request.address()));
        club.setWhatsapp(normalizeWhatsapp(request.whatsapp()));
        return toProfile(club);
    }

    @Transactional
    public ClubProfileResponse uploadLogo(long clubId, FileUpload file) {
        access.requireClubAdmin(clubId);
        Club club = requireClub(clubId);
        String previousUrl = club.getLogoUrl();
        String url = upload(clubId, MinioObjectStorage.ClubImageKind.LOGO, file);
        club.setLogoUrl(url);
        // O logo reaproveita a chave quando a extensão não muda; apagar o anterior apagaria o novo.
        String previousKey = storage.extractObjectKey(previousUrl);
        if (previousKey != null && !previousKey.equals(storage.extractObjectKey(url))) {
            storage.deleteObjectIfPresent(previousUrl);
        }
        return toProfile(club);
    }

    @Transactional
    public ClubPhotoResponse addPhoto(long clubId, FileUpload file) {
        access.requireClubAdmin(clubId);
        Club club = requireClub(clubId);
        List<ClubPhoto> photos = photoRepository.listByClub(clubId);
        if (photos.size() >= MAX_PHOTOS) {
            throw new BusinessRuleException("Limite de 10 fotos atingido");
        }
        String url = upload(clubId, MinioObjectStorage.ClubImageKind.PHOTO, file);
        int position = photos.isEmpty() ? 0 : photos.get(photos.size() - 1).getPosition() + 1;
        ClubPhoto photo = ClubPhoto.create(club, url, position);
        photoRepository.persist(photo);
        return ClubPhotoResponse.from(photo, storage);
    }

    @Transactional
    public void removePhoto(long clubId, long photoId) {
        access.requireClubAdmin(clubId);
        ClubPhoto photo = photoRepository.findByIdAndClub(photoId, clubId)
                .orElseThrow(() -> new NotFoundException("Foto não encontrada"));
        photo.markExcluded();
        storage.deleteObjectIfPresent(photo.getUrl());
    }

    @Transactional
    public ClubAdminsResponse listAdmins(long clubId) {
        access.requireClubAdmin(clubId);
        Instant now = clock.instant();
        return new ClubAdminsResponse(
                adminRepository.listActiveOfClub(clubId).stream().map(ClubAdminsResponse.Admin::from).toList(),
                inviteRepository.listPendingOfClub(clubId, InviteKind.CLUB_ADMIN).stream()
                        .map(invite -> PendingInviteResponse.from(invite, now)).toList());
    }

    public InviteResponse inviteAdmin(long clubId, String email) {
        access.requireClubAdmin(clubId);
        return inviteService.inviteClubAdmin(clubId, email);
    }

    public InviteResponse inviteCoach(long clubId, String email) {
        access.requireClubAdmin(clubId);
        return inviteService.inviteCoach(clubId, email);
    }

    private Club requireClub(long clubId) {
        return clubRepository.findActiveById(clubId)
                .orElseThrow(() -> new NotFoundException("Clube não encontrado"));
    }

    private ClubProfileResponse toProfile(Club club) {
        return ClubProfileResponse.from(club, photoRepository.listByClub(club.getId()), storage);
    }

    private String upload(long clubId, MinioObjectStorage.ClubImageKind kind, FileUpload file) {
        if (file == null || file.size() <= 0) {
            throw new BadRequestException("Envie um arquivo de imagem no campo file");
        }
        try (InputStream in = Files.newInputStream(file.uploadedFile())) {
            return storage.uploadClubImage(clubId, kind, in, file.size(), file.contentType());
        } catch (IOException e) {
            throw new BadRequestException("Não foi possível ler a imagem enviada");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeWhatsapp(String raw) {
        return raw == null || raw.isBlank() ? null : PhoneNumber.digitsOf(raw, "WhatsApp inválido");
    }
}
