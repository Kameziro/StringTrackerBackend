package br.com.stringtracker.resource;

import br.com.stringtracker.dto.ClubResponse;
import br.com.stringtracker.repository.ClubRepository;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/clubs")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ClubResource {

    @Inject
    ClubRepository clubRepository;

    @GET
    public List<ClubResponse> list() {
        return clubRepository.listAllActive().stream()
                .map(ClubResponse::from)
                .toList();
    }
}
