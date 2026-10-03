package br.com.stringtracker.resource;

import br.com.stringtracker.dto.InviteAcceptedResponse;
import br.com.stringtracker.dto.InviteInfoResponse;
import br.com.stringtracker.service.InviteService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Consulta é pública: o token (256 bits) já é a credencial e a tela de aceite precisa mostrar
 * o clube antes do login. Aceitar exige usuário logado.
 */
@Path("/api/invites")
@Produces(MediaType.APPLICATION_JSON)
public class InviteResource {

    @Inject
    InviteService inviteService;

    @GET
    @Path("/{token}")
    @PermitAll
    public InviteInfoResponse get(@PathParam("token") String token) {
        return inviteService.describe(token);
    }

    @POST
    @Path("/{token}/accept")
    @Authenticated
    public InviteAcceptedResponse accept(@PathParam("token") String token) {
        return inviteService.accept(token);
    }
}
