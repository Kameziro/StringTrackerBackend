package br.com.stringtracker.resource.admin;

import br.com.stringtracker.dto.ClubCoachesResponse;
import br.com.stringtracker.dto.CoachPricesResponse;
import br.com.stringtracker.dto.CreateScheduleBlockRequest;
import br.com.stringtracker.dto.DayBlockRequest;
import br.com.stringtracker.dto.DayBlockResponse;
import br.com.stringtracker.dto.UnlinkCoachResponse;
import br.com.stringtracker.dto.UpdateCoachPricesRequest;
import br.com.stringtracker.service.CoachService;
import br.com.stringtracker.service.DayBlockService;
import br.com.stringtracker.service.ScheduleService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// O caminho da classe é mais longo que o de ClubAdminResource para vencer a seleção de classe do JAX-RS.
@Path("/api/admin/clubs/{clubId}/coaches")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachAdminResource {

    @Inject
    CoachService coachService;

    @Inject
    ScheduleService scheduleService;

    @Inject
    DayBlockService dayBlockService;

    @GET
    public ClubCoachesResponse listCoaches(@PathParam("clubId") long clubId) {
        return coachService.listCoaches(clubId);
    }

    @PUT
    @Path("/{coachId}/prices")
    public CoachPricesResponse updatePrices(
            @PathParam("clubId") long clubId,
            @PathParam("coachId") long coachId,
            @Valid UpdateCoachPricesRequest request
    ) {
        return coachService.updatePrices(clubId, coachId, request);
    }

    @POST
    @Path("/{coachId}/blocks")
    public Response createBlock(
            @PathParam("clubId") long clubId,
            @PathParam("coachId") long coachId,
            @Valid CreateScheduleBlockRequest request
    ) {
        return Response.status(Response.Status.CREATED)
                .entity(scheduleService.createBlock(clubId, coachId, request))
                .build();
    }

    @POST
    @Path("/{coachId}/day-blocks")
    public DayBlockResponse blockDay(
            @PathParam("clubId") long clubId,
            @PathParam("coachId") long coachId,
            @Valid DayBlockRequest request
    ) {
        return dayBlockService.blockForClub(clubId, coachId, request);
    }

    @DELETE
    @Path("/{coachId}")
    public UnlinkCoachResponse unlink(
            @PathParam("clubId") long clubId,
            @PathParam("coachId") long coachId,
            @QueryParam("confirm") boolean confirm
    ) {
        return coachService.unlink(clubId, coachId, confirm);
    }
}
