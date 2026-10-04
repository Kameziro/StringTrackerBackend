package br.com.stringtracker.resource.coach;

import br.com.stringtracker.dto.BookingResponse;
import br.com.stringtracker.service.BookingCancellationService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/coach/me/bookings")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachBookingResource {

    @Inject
    BookingCancellationService cancellationService;

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    public BookingResponse cancel(@PathParam("id") long id) {
        return cancellationService.cancelByCoach(id);
    }
}
