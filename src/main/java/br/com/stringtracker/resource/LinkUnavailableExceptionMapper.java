package br.com.stringtracker.resource;

import br.com.stringtracker.service.LinkUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class LinkUnavailableExceptionMapper implements ExceptionMapper<LinkUnavailableException> {

    @Override
    public Response toResponse(LinkUnavailableException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("code", LinkUnavailableException.CODE, "message", exception.getMessage()))
                .build();
    }
}
