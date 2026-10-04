package br.com.stringtracker.resource.coach;

import br.com.stringtracker.dto.CoachAgendaResponse;
import br.com.stringtracker.service.CoachAgendaService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

// O caminho da classe é mais longo que o de CoachMeResource para vencer a seleção de classe do JAX-RS.
@Path("/api/coach/me/agenda")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CoachAgendaResource {

    @Inject
    CoachAgendaService agendaService;

    @GET
    public CoachAgendaResponse agenda(@QueryParam("from") String from, @QueryParam("to") String to) {
        return agendaService.agenda(from, to);
    }
}
