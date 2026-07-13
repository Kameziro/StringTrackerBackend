package br.com.stringtracker.resource;

import br.com.stringtracker.dto.CreateRacketRequest;
import br.com.stringtracker.dto.RacketResponse;
import br.com.stringtracker.service.CurrentUserService;
import br.com.stringtracker.service.RacketService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/rackets")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RacketResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    RacketService racketService;

    @GET
    public List<RacketResponse> list() {
        return racketService.listFor(currentUserService.requireCurrentUser());
    }

    @POST
    public Response create(@Valid CreateRacketRequest request) {
        RacketResponse created = racketService.create(currentUserService.requireCurrentUser(), request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
