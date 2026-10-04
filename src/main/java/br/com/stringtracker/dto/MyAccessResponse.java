package br.com.stringtracker.dto;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.service.MinioObjectStorage;

import java.util.List;

/**
 * Os papéis do usuário logado: admin da plataforma, admin de quais clubes (ativos, por nome) e se é professor de
 * algum clube ativo. O painel decide a primeira tela com isso.
 */
public record MyAccessResponse(boolean platformAdmin, List<AdminClub> adminClubs, boolean coach) {

    public record AdminClub(long id, String name, String logoUrl) {

        public static AdminClub from(Club club, MinioObjectStorage storage) {
            return new AdminClub(club.getId(), club.getName(), storage.toClientMediaUrl(club.getLogoUrl()));
        }
    }
}
