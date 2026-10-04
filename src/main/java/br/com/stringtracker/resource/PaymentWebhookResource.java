package br.com.stringtracker.resource;

import br.com.stringtracker.service.payment.PaymentWebhookService;
import br.com.stringtracker.service.payment.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Notificações do Mercado Pago. A rota é pública: quem autentica é o {@code x-signature}, validado antes de
 * qualquer leitura do corpo. O {@code data.id} da assinatura vem da query string da notificação.
 */
@Path("/api/payments/mercadopago/webhook")
@Consumes(MediaType.WILDCARD)
public class PaymentWebhookResource {

    @Inject
    WebhookSignatureVerifier signatureVerifier;

    @Inject
    PaymentWebhookService webhookService;

    @Inject
    ObjectMapper json;

    @POST
    public Response receive(
            @HeaderParam("x-signature") String signature,
            @HeaderParam("x-request-id") String requestId,
            @QueryParam("data.id") String dataId,
            String body
    ) {
        if (!signatureVerifier.isValid(signature, requestId, dataId)) {
            throw new NotAuthorizedException("Assinatura inválida", "Signature");
        }
        webhookService.handle("mp-" + eventId(body, requestId), dataId);
        return Response.ok().build();
    }

    /** Id da notificação no corpo, que se repete nas reentregas; sem ele, o x-request-id. */
    private String eventId(String body, String requestId) {
        try {
            JsonNode id = json.readTree(body).path("id");
            return id.isMissingNode() || id.isNull() ? requestId : id.asText();
        } catch (Exception e) {
            throw new BadRequestException("Corpo da notificação inválido");
        }
    }
}
