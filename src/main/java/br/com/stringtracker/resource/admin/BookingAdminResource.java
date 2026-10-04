package br.com.stringtracker.resource.admin;

import br.com.stringtracker.dto.BookingResponse;
import br.com.stringtracker.dto.ClubBookingsResponse;
import br.com.stringtracker.dto.CreateManualBookingRequest;
import br.com.stringtracker.service.BookingCancellationService;
import br.com.stringtracker.service.ClubBookingsService;
import br.com.stringtracker.service.ManualBookingService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// O caminho da classe é mais longo que o de ClubAdminResource para vencer a seleção de classe do JAX-RS.
@Path("/api/admin/clubs/{clubId}/bookings")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookingAdminResource {

    @Inject
    BookingCancellationService cancellationService;

    @Inject
    ClubBookingsService clubBookingsService;

    @Inject
    ManualBookingService manualBookingService;

    @GET
    public ClubBookingsResponse list(@PathParam("clubId") long clubId, @QueryParam("from") String from,
                                     @QueryParam("to") String to) {
        return clubBookingsService.list(clubId, from, to);
    }

    @POST
    public Response createManual(@PathParam("clubId") long clubId, @Valid CreateManualBookingRequest request) {
        return Response.status(Response.Status.CREATED)
                .entity(manualBookingService.createForClub(clubId, request))
                .build();
    }

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    public BookingResponse cancel(@PathParam("clubId") long clubId, @PathParam("id") long id) {
        return cancellationService.cancelByClub(clubId, id);
    }
}
