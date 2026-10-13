package br.com.stringtracker.dto;

import br.com.stringtracker.model.ClubAdmin;

import java.util.List;

/** Admins do clube e os convites de admin ainda não aceitos. */
public record ClubAdminsResponse(List<Admin> admins, List<PendingInviteResponse> pendingInvites) {

    public record Admin(long userId, String name, String email) {

        public static Admin from(ClubAdmin admin) {
            return new Admin(admin.getUser().getId(), admin.getUser().getName(), admin.getUser().getEmail());
        }
    }
}
