package br.com.stringtracker.resource;

import br.com.stringtracker.dto.AvailabilitySlotResponse;
import br.com.stringtracker.dto.ProfileResponse;
import br.com.stringtracker.dto.RegisterDeviceTokenRequest;
import br.com.stringtracker.dto.UpdateAvailabilityRequest;
import br.com.stringtracker.dto.UpdateProfileRequest;
import br.com.stringtracker.service.CurrentUserService;
import br.com.stringtracker.service.ProfileService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@Path("/api/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ProfileResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ProfileService profileService;

    @GET
    @Path("/profile")
    public ProfileResponse getProfile() {
        return profileService.getProfile(currentUserService.requireCurrentUser());
    }

    @PUT
    @Path("/profile")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProfileResponse updateProfile(@Valid UpdateProfileRequest request) {
        return profileService.updateProfile(currentUserService.requireCurrentUser(), request);
    }

    @POST
    @Path("/profile/avatar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ProfileResponse uploadAvatar(@RestForm("file") FileUpload file) {
        return profileService.uploadAvatar(currentUserService.requireCurrentUser(), file);
    }

    @GET
    @Path("/availability")
    public List<AvailabilitySlotResponse> listAvailability() {
        return profileService.listAvailability(currentUserService.requireCurrentUser());
    }

    @PUT
    @Path("/availability")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AvailabilitySlotResponse> updateAvailability(@Valid UpdateAvailabilityRequest request) {
        return profileService.replaceAvailability(currentUserService.requireCurrentUser(), request);
    }

    @POST
    @Path("/device-token")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerDeviceToken(@Valid RegisterDeviceTokenRequest request) {
        profileService.registerDeviceToken(currentUserService.requireCurrentUser(), request);
        return Response.noContent().build();
    }
}
