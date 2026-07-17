package br.com.stringtracker.resource;

import br.com.stringtracker.service.UserAlreadyExistsException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class UserAlreadyExistsExceptionMapper implements ExceptionMapper<UserAlreadyExistsException> {
    @Override
    public Response toResponse(UserAlreadyExistsException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", exception.getMessage()))
                .build();
    }
}
