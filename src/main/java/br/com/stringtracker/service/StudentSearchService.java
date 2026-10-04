package br.com.stringtracker.service;

import br.com.stringtracker.dto.StudentSummaryResponse;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.util.List;

/**
 * Busca de aluno com conta para a reserva manual (MANUAL-02), por nome (parte do nome) ou por e-mail (exato).
 * Só admin do clube ou professor consultam, e o e-mail não aparece inteiro nos resultados.
 */
@ApplicationScoped
public class StudentSearchService {

    private static final int MIN_QUERY_LENGTH = 3;
    private static final int LIMIT = 10;

    @Inject
    ClubAccessService access;

    @Inject
    UserRepository userRepository;

    @Transactional
    public List<StudentSummaryResponse> searchForClub(long clubId, String query) {
        access.requireClubAdmin(clubId);
        return search(query);
    }

    @Transactional
    public List<StudentSummaryResponse> searchForCoach(String query) {
        access.requireCurrentCoach();
        return search(query);
    }

    private List<StudentSummaryResponse> search(String query) {
        String text = query == null ? "" : query.trim();
        if (text.length() < MIN_QUERY_LENGTH) {
            throw new BadRequestException("Digite ao menos 3 letras para buscar");
        }
        List<User> users = text.contains("@")
                ? userRepository.findActiveByEmailIgnoreCase(text).stream().toList()
                : userRepository.searchActiveByName(text, LIMIT);
        return users.stream().map(StudentSummaryResponse::from).toList();
    }
}
