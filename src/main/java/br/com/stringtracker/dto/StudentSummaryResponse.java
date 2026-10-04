package br.com.stringtracker.dto;

import br.com.stringtracker.model.User;

/** Aluno encontrado na busca da reserva manual. O e-mail sai mascarado: serve para distinguir homônimos, não para listar contatos. */
public record StudentSummaryResponse(long id, String name, String emailHint) {

    public static StudentSummaryResponse from(User user) {
        return new StudentSummaryResponse(user.getId(), user.getName(), mask(user.getEmail()));
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? email : email.charAt(0) + "***" + email.substring(at);
    }
}
