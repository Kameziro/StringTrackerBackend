package br.com.stringtracker.resource;

import br.com.stringtracker.dto.MyLessonsResponse;
import br.com.stringtracker.service.MyLessonsService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

// O caminho da classe é mais longo que o de ProfileResource (/api/me) para vencer a seleção de classe do JAX-RS.
@Path("/api/me/lessons")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class MyLessonsResource {

    @Inject
    MyLessonsService myLessonsService;

    @GET
    public MyLessonsResponse list() {
        return myLessonsService.list();
    }
}
