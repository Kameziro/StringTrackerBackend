package br.com.stringtracker.resource;

import br.com.stringtracker.dto.CityResponse;
import br.com.stringtracker.dto.StateResponse;
import br.com.stringtracker.repository.CityRepository;
import br.com.stringtracker.repository.StateRepository;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/geo")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
public class GeoResource {

    @Inject
    StateRepository stateRepository;

    @Inject
    CityRepository cityRepository;

    @GET
    @Path("/states")
    public List<StateResponse> listStates() {
        return stateRepository.listAllOrdered().stream()
                .map(StateResponse::from)
                .toList();
    }

    @GET
    @Path("/cities")
    public List<CityResponse> listCities(@QueryParam("stateId") Long stateId) {
        if (stateId == null) {
            throw new BadRequestException("Informe stateId");
        }
        return cityRepository.listByStateId(stateId).stream()
                .map(CityResponse::from)
                .toList();
    }
}
