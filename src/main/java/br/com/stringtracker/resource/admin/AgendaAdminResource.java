package br.com.stringtracker.resource.admin;

import br.com.stringtracker.dto.AgendaResponse;
import br.com.stringtracker.service.AgendaService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

// O caminho da classe é mais longo que o de ClubAdminResource para vencer a seleção de classe do JAX-RS.
@Path("/api/admin/clubs/{clubId}/agenda")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AgendaAdminResource {

    @Inject
    AgendaService agendaService;

    @GET
    public AgendaResponse week(@PathParam("clubId") long clubId, @QueryParam("week") String week) {
        return agendaService.weekAgenda(clubId, week);
    }
}
