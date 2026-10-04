package br.com.stringtracker.resource;

import br.com.stringtracker.dto.LessonCoachResponse;
import br.com.stringtracker.service.LessonDiscoveryService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/lessons/coaches")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class LessonCoachResource {

    @Inject
    LessonDiscoveryService discoveryService;

    @GET
    @Path("/{coachId}")
    public LessonCoachResponse getCoach(@PathParam("coachId") long coachId, @QueryParam("clubId") Long clubId) {
        return discoveryService.getCoach(coachId, clubId);
    }
}
