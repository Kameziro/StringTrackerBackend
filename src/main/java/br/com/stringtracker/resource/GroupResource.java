package br.com.stringtracker.resource;

import br.com.stringtracker.dto.CreateGroupRequest;
import br.com.stringtracker.dto.GroupDetailResponse;
import br.com.stringtracker.dto.GroupResponse;
import br.com.stringtracker.service.CurrentUserService;
import br.com.stringtracker.service.GroupService;
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

@Path("/api/groups")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GroupResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    GroupService groupService;

    @GET
    public List<GroupResponse> list(@QueryParam("mine") boolean mine) {
        var user = currentUserService.requireCurrentUser();
        return mine ? groupService.listMine(user) : groupService.listAll(user);
    }

    @GET
    @Path("/{id}")
    public GroupDetailResponse get(@PathParam("id") Long id) {
        return groupService.getDetail(currentUserService.requireCurrentUser(), id);
    }

    @POST
    public Response create(@Valid CreateGroupRequest request) {
        GroupResponse created = groupService.create(currentUserService.requireCurrentUser(), request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @Path("/{id}/join")
    public GroupResponse join(@PathParam("id") Long id) {
        return groupService.join(currentUserService.requireCurrentUser(), id);
    }

    @POST
    @Path("/{id}/leave")
    public GroupResponse leave(@PathParam("id") Long id) {
        return groupService.leave(currentUserService.requireCurrentUser(), id);
    }
}
