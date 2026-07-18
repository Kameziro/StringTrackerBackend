package br.com.stringtracker.resource;

import br.com.stringtracker.dto.CreateGroupRequest;
import br.com.stringtracker.dto.GroupDetailResponse;
import br.com.stringtracker.dto.GroupResponse;
import br.com.stringtracker.dto.UpdateGroupMemberRoleRequest;
import br.com.stringtracker.service.CurrentUserService;
import br.com.stringtracker.service.GroupService;
import br.com.stringtracker.service.MinioObjectStorage;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

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

    @POST
    @Path("/{id}/avatar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public GroupDetailResponse uploadAvatar(
            @PathParam("id") Long id,
            @RestForm("file") FileUpload file
    ) {
        return groupService.uploadImage(
                currentUserService.requireCurrentUser(),
                id,
                MinioObjectStorage.GroupImageKind.AVATAR,
                file
        );
    }

    @POST
    @Path("/{id}/banner")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public GroupDetailResponse uploadBanner(
            @PathParam("id") Long id,
            @RestForm("file") FileUpload file
    ) {
        return groupService.uploadImage(
                currentUserService.requireCurrentUser(),
                id,
                MinioObjectStorage.GroupImageKind.BANNER,
                file
        );
    }

    @PUT
    @Path("/{id}/members/{userId}/role")
    public GroupDetailResponse updateMemberRole(
            @PathParam("id") Long id,
            @PathParam("userId") Long userId,
            @Valid UpdateGroupMemberRoleRequest request
    ) {
        return groupService.updateMemberRole(
                currentUserService.requireCurrentUser(),
                id,
                userId,
                request
        );
    }
}
