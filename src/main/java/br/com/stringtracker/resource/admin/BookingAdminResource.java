package br.com.stringtracker.resource.admin;

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

// O caminho da classe é mais longo que o de ClubAdminResource para vencer a seleção de classe do JAX-RS.
@Path("/api/admin/clubs/{clubId}/bookings")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookingAdminResource {

    @Inject
    BookingCancellationService cancellationService;

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    public BookingResponse cancel(@PathParam("clubId") long clubId, @PathParam("id") long id) {
        return cancellationService.cancelByClub(clubId, id);
    }
}
