package br.com.stringtracker.resource.admin;

import br.com.stringtracker.dto.CreateClubRequest;
import br.com.stringtracker.dto.InviteRequest;
import br.com.stringtracker.dto.PlatformClubResponse;
import br.com.stringtracker.service.PlatformService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/platform/clubs")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlatformResource {

    @Inject
    PlatformService platformService;

    @GET
    public List<PlatformClubResponse> list() {
        return platformService.listClubs();
    }

    @POST
    public Response create(@Valid CreateClubRequest request) {
        return Response.status(Response.Status.CREATED).entity(platformService.createClub(request)).build();
    }

    @POST
    @Path("/{id}/admin-invites")
    public Response inviteAdmin(@PathParam("id") long clubId, @Valid InviteRequest request) {
        return Response.status(Response.Status.CREATED)
                .entity(platformService.inviteAdmin(clubId, request.email()))
                .build();
    }
}
