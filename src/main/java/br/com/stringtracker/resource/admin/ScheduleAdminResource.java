package br.com.stringtracker.resource.admin;

import br.com.stringtracker.dto.ClubBlocksResponse;
import br.com.stringtracker.service.ScheduleService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// O caminho da classe é mais longo que o de ClubAdminResource para vencer a seleção de classe do JAX-RS.
@Path("/api/admin/clubs/{clubId}/blocks")
@Authenticated
public class ScheduleAdminResource {

    @Inject
    ScheduleService scheduleService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ClubBlocksResponse listBlocks(@PathParam("clubId") long clubId) {
        return scheduleService.listBlocks(clubId);
    }

    @DELETE
    @Path("/{blockId}")
    public Response removeBlock(@PathParam("clubId") long clubId, @PathParam("blockId") long blockId) {
        scheduleService.removeBlock(clubId, blockId);
        return Response.noContent().build();
    }
}
