package br.com.stringtracker.resource;

import br.com.stringtracker.dto.PlayerResponse;
import br.com.stringtracker.model.User;
import br.com.stringtracker.service.CurrentUserService;
import br.com.stringtracker.service.OpenGameService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/players")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class PlayerResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    OpenGameService openGameService;

    @GET
    public List<PlayerResponse> list(@QueryParam("availableToday") boolean availableToday) {
        User user = currentUserService.requireCurrentUser();
        if (user.getCategory() == null) {
            throw new BadRequestException("Complete seu perfil com a categoria");
        }
        if (user.getCity() == null) {
            throw new BadRequestException("Informe sua cidade no perfil");
        }
        if (!availableToday) {
            return List.of();
        }
        return openGameService.listAvailablePlayers(user.getCategory(), user.getCity().getId(), user.getId());
    }
}
