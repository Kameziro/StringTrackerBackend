package br.com.stringtracker.resource;

import br.com.stringtracker.service.SlotConflictException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SlotConflictExceptionMapper implements ExceptionMapper<SlotConflictException> {

    @Override
    public Response toResponse(SlotConflictException exception) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.TEXT_PLAIN)
                .entity(exception.getMessage())
                .build();
    }
}
