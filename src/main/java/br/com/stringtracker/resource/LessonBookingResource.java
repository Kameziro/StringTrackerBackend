package br.com.stringtracker.resource;

import br.com.stringtracker.dto.BookingResponse;
import br.com.stringtracker.dto.CreateBookingRequest;
import br.com.stringtracker.service.BookingService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/lessons/bookings")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LessonBookingResource {

    @Inject
    BookingService bookingService;

    @POST
    public Response hold(@Valid CreateBookingRequest request) {
        return Response.status(Response.Status.CREATED).entity(bookingService.hold(request)).build();
    }

    @GET
    @Path("/{id}")
    public BookingResponse get(@PathParam("id") long id) {
        return bookingService.get(id);
    }
}
