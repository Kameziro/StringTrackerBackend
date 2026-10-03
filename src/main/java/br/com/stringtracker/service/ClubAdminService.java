package br.com.stringtracker.service;

import br.com.stringtracker.dto.ClubProfileResponse;
import br.com.stringtracker.dto.InviteResponse;
import br.com.stringtracker.dto.UpdateClubProfileRequest;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.repository.ClubRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.regex.Pattern;

/** Perfil do clube e convites feitos pelo admin. Todo método exige ser admin do clube pedido. */
@ApplicationScoped
public class ClubAdminService {

    private static final Pattern WHATSAPP_FORMAT = Pattern.compile("^\\+?[\\d\\s().-]+$");
    private static final int WHATSAPP_MIN_DIGITS = 10;
    private static final int WHATSAPP_MAX_DIGITS = 15;

    @Inject
    ClubAccessService access;

    @Inject
    ClubRepository clubRepository;

    @Inject
    InviteService inviteService;

    @Inject
    MinioObjectStorage storage;

    @Transactional
    public ClubProfileResponse getProfile(long clubId) {
        access.requireClubAdmin(clubId);
        return ClubProfileResponse.from(requireClub(clubId), storage);
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
        return ClubProfileResponse.from(club, storage);
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Guarda só os dígitos, o formato que o link wa.me aceita. */
    private static String normalizeWhatsapp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("\\D", "");
        if (!WHATSAPP_FORMAT.matcher(trimmed).matches()
                || digits.length() < WHATSAPP_MIN_DIGITS
                || digits.length() > WHATSAPP_MAX_DIGITS) {
            throw new BadRequestException("WhatsApp inválido");
        }
        return digits;
    }
}
