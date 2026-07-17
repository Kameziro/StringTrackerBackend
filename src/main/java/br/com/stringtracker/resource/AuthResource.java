package br.com.stringtracker.resource;

import br.com.stringtracker.dto.EmailAvailabilityResponse;
import br.com.stringtracker.dto.LoginRequest;
import br.com.stringtracker.dto.LoginResponse;
import br.com.stringtracker.dto.RefreshRequest;
import br.com.stringtracker.dto.RegisterRequest;
import br.com.stringtracker.service.AuthService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/auth")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
        LoginResponse tokens = authService.login(request.getUsername(), request.getPassword());
        return Response.ok(tokens).build();
    }

    @POST
    @Path("/refresh")
    public Response refresh(@Valid RefreshRequest request) {
        LoginResponse tokens = authService.refresh(request.getRefreshToken());
        return Response.ok(tokens).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Valid RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return Response.noContent().build();
    }

    @GET
    @Path("/email-available")
    public Response emailAvailable(
            @QueryParam("email") @NotBlank @Email String email
    ) {
        boolean available = authService.isEmailAvailable(email);
        return Response.ok(new EmailAvailabilityResponse(available)).build();
    }

    @POST
    @Path("/register")
    public Response register(@Valid RegisterRequest request) {
        LoginResponse tokens = authService.register(
                request.getEmail(),
                request.getPassword(),
                request.getName(),
                request.getCategory(),
                request.getCityId()
        );
        return Response.status(Response.Status.CREATED).entity(tokens).build();
    }
}
