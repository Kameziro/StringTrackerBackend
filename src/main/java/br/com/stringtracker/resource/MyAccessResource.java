package br.com.stringtracker.resource;

import br.com.stringtracker.dto.MyAccessResponse;
import br.com.stringtracker.service.MyAccessService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

// O caminho da classe é mais longo que o de ProfileResource (/api/me) para vencer a seleção de classe do JAX-RS.
@Path("/api/me/access")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class MyAccessResource {

    @Inject
    MyAccessService myAccessService;

    @GET
    public MyAccessResponse get() {
        return myAccessService.get();
    }
}
