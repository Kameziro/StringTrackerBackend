package br.com.stringtracker.resource.coach;

import br.com.stringtracker.dto.CoachOffersResponse;
import br.com.stringtracker.dto.DayBlockRequest;
import br.com.stringtracker.dto.DayBlockResponse;
import br.com.stringtracker.dto.UpdateCoachOffersRequest;
import br.com.stringtracker.service.CoachService;
import br.com.stringtracker.service.DayBlockService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/coach/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachMeResource {

    @Inject
    CoachService coachService;

    @Inject
    DayBlockService dayBlockService;

    @PUT
    @Path("/offers")
    public CoachOffersResponse updateOffers(@Valid UpdateCoachOffersRequest request) {
        return coachService.updateOffers(request);
    }

    @POST
    @Path("/day-blocks")
    public DayBlockResponse blockDay(@Valid DayBlockRequest request) {
        return dayBlockService.blockForCoach(request);
    }
}
