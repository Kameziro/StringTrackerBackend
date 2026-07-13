package br.com.stringtracker.resource;

import br.com.stringtracker.service.FreeTierLimitExceededException;
import br.com.stringtracker.service.FreemiumPolicy;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class FreeTierLimitExceededExceptionMapper implements ExceptionMapper<FreeTierLimitExceededException> {

    @Override
    public Response toResponse(FreeTierLimitExceededException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.TEXT_PLAIN)
                .entity(FreemiumPolicy.FREE_TIER_LIMIT_MESSAGE)
                .build();
    }
}
