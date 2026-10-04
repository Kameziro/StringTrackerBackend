package br.com.stringtracker.resource.admin;

import br.com.stringtracker.dto.CoachPricesResponse;
import br.com.stringtracker.dto.UpdateCoachPricesRequest;
import br.com.stringtracker.service.CoachService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

// O caminho da classe é mais longo que o de ClubAdminResource para vencer a seleção de classe do JAX-RS.
@Path("/api/admin/clubs/{clubId}/coaches")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachAdminResource {

    @Inject
    CoachService coachService;

    @PUT
    @Path("/{coachId}/prices")
    public CoachPricesResponse updatePrices(
            @PathParam("clubId") long clubId,
            @PathParam("coachId") long coachId,
            @Valid UpdateCoachPricesRequest request
    ) {
        return coachService.updatePrices(clubId, coachId, request);
    }
}
