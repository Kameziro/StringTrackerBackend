package br.com.stringtracker.resource;

import br.com.stringtracker.service.BusinessRuleException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BusinessRuleExceptionMapper implements ExceptionMapper<BusinessRuleException> {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(BusinessRuleException exception) {
        return Response.status(UNPROCESSABLE_ENTITY)
                .type(MediaType.TEXT_PLAIN)
                .entity(exception.getMessage())
                .build();
    }
}
