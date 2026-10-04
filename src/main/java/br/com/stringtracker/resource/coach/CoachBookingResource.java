package br.com.stringtracker.resource.coach;

import br.com.stringtracker.dto.BookingResponse;
import br.com.stringtracker.dto.CreateManualBookingRequest;
import br.com.stringtracker.service.BookingCancellationService;
import br.com.stringtracker.service.ManualBookingService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/coach/me/bookings")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachBookingResource {

    @Inject
    BookingCancellationService cancellationService;

    @Inject
    ManualBookingService manualBookingService;

    @POST
    public Response createManual(@Valid CreateManualBookingRequest request) {
        return Response.status(Response.Status.CREATED).entity(manualBookingService.createForCoach(request)).build();
    }

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    public BookingResponse cancel(@PathParam("id") long id) {
        return cancellationService.cancelByCoach(id);
    }
}
