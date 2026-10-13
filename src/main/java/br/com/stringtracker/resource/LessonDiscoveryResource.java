package br.com.stringtracker.resource;

import br.com.stringtracker.dto.LessonClubResponse;
import br.com.stringtracker.dto.LessonClubSummaryResponse;
import br.com.stringtracker.service.LessonDiscoveryService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/lessons/clubs")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class LessonDiscoveryResource {

    @Inject
    LessonDiscoveryService discoveryService;

    @GET
    public List<LessonClubSummaryResponse> listClubs(@QueryParam("cityId") Long cityId) {
        return discoveryService.listClubs(cityId);
    }

    @GET
    @Path("/{clubId}")
    public LessonClubResponse getClub(@PathParam("clubId") long clubId) {
        return discoveryService.getClub(clubId);
    }
}
