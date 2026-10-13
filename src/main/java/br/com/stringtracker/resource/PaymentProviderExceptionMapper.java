package br.com.stringtracker.resource;

import br.com.stringtracker.service.PaymentProviderException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PaymentProviderExceptionMapper implements ExceptionMapper<PaymentProviderException> {

    @Override
    public Response toResponse(PaymentProviderException exception) {
        return Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.TEXT_PLAIN)
                .entity(exception.getMessage())
                .build();
    }
}
