package br.com.stringtracker.resource;

import br.com.stringtracker.dto.CreateOpenGameRequest;
import br.com.stringtracker.dto.OpenGameResponse;
import br.com.stringtracker.model.User;
import br.com.stringtracker.service.CurrentUserService;
import br.com.stringtracker.service.OpenGameService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/games")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenGameResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    OpenGameService openGameService;

    @GET
    public List<OpenGameResponse> list(@QueryParam("mine") boolean mine) {
        User user = currentUserService.requireCurrentUser();
        if (mine) {
            return openGameService.listMine(user);
        }
        return openGameService.listOpenVisibleTo(user);
    }

    @POST
    public Response create(@Valid CreateOpenGameRequest request) {
        OpenGameResponse created = openGameService.create(currentUserService.requireCurrentUser(), request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}")
    public OpenGameResponse get(@PathParam("id") Long id) {
        return openGameService.get(id);
    }

    @POST
    @Path("/{id}/interest")
    public OpenGameResponse interest(@PathParam("id") Long id) {
        return openGameService.expressInterest(currentUserService.requireCurrentUser(), id);
    }

    @POST
    @Path("/{id}/decline")
    public OpenGameResponse decline(@PathParam("id") Long id) {
        return openGameService.decline(currentUserService.requireCurrentUser(), id);
    }

    @POST
    @Path("/{id}/confirm")
    public OpenGameResponse confirm(@PathParam("id") Long id) {
        return openGameService.confirm(currentUserService.requireCurrentUser(), id);
    }
}
