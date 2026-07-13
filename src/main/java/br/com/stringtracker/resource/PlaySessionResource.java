package br.com.stringtracker.resource;

import br.com.stringtracker.dto.CreatePlaySessionRequest;
import br.com.stringtracker.dto.PlaySessionResponse;
import br.com.stringtracker.service.CurrentUserService;
import br.com.stringtracker.service.PlaySessionService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/sessions")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlaySessionResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    PlaySessionService playSessionService;

    @POST
    public Response create(@Valid CreatePlaySessionRequest request) {
        PlaySessionResponse created = playSessionService.create(
                currentUserService.requireCurrentUser(),
                request
        );
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
