package br.com.stringtracker.resource;

import br.com.stringtracker.service.InviteUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InviteUnavailableExceptionMapper implements ExceptionMapper<InviteUnavailableException> {

    @Override
    public Response toResponse(InviteUnavailableException exception) {
        return Response.status(Response.Status.GONE)
                .type(MediaType.TEXT_PLAIN)
                .entity(exception.getMessage())
                .build();
    }
}
