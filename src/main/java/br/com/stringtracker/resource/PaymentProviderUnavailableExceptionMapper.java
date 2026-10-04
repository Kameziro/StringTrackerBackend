package br.com.stringtracker.resource;

import br.com.stringtracker.service.PaymentProviderUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PaymentProviderUnavailableExceptionMapper implements ExceptionMapper<PaymentProviderUnavailableException> {

    @Override
    public Response toResponse(PaymentProviderUnavailableException exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(MediaType.TEXT_PLAIN)
                .entity(exception.getMessage())
                .build();
    }
}
