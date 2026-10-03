package br.com.stringtracker.resource.admin;

import br.com.stringtracker.dto.ClubProfileResponse;
import br.com.stringtracker.dto.InviteRequest;
import br.com.stringtracker.dto.UpdateClubProfileRequest;
import br.com.stringtracker.service.ClubAdminService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin/clubs/{clubId}")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClubAdminResource {

    @Inject
    ClubAdminService clubAdminService;

    @GET
    @Path("/profile")
    public ClubProfileResponse getProfile(@PathParam("clubId") long clubId) {
        return clubAdminService.getProfile(clubId);
    }

    @PUT
    @Path("/profile")
    public ClubProfileResponse updateProfile(
            @PathParam("clubId") long clubId,
            @Valid UpdateClubProfileRequest request
    ) {
        return clubAdminService.updateProfile(clubId, request);
    }

    @POST
    @Path("/admin-invites")
    public Response inviteAdmin(@PathParam("clubId") long clubId, @Valid InviteRequest request) {
        return Response.status(Response.Status.CREATED)
                .entity(clubAdminService.inviteAdmin(clubId, request.email()))
                .build();
    }

    // SPEC_DEVIATION: o design define InviteService.inviteCoach (PROF-01) mas nenhuma rota que o chame.
    // Reason: sem esta rota o admin não consegue convidar professores; ela fica junto de admin-invites.
    @POST
    @Path("/coach-invites")
    public Response inviteCoach(@PathParam("clubId") long clubId, @Valid InviteRequest request) {
        return Response.status(Response.Status.CREATED)
                .entity(clubAdminService.inviteCoach(clubId, request.email()))
                .build();
    }
}
