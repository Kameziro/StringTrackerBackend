package br.com.stringtracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * Orders API do Mercado Pago (formato validado no spike T1). Todas as chamadas levam o token do clube em
 * {@code Authorization: Bearer ...}; as que mudam estado levam {@code X-Idempotency-Key}.
 */
@RegisterRestClient(configKey = "mercadopago")
@Path("/v1/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MercadoPagoClient {

    @POST
    Order createOrder(
            @HeaderParam("Authorization") String authorization,
            @HeaderParam("X-Idempotency-Key") String idempotencyKey,
            CreateOrderRequest request
    );

    @GET
    @Path("/{orderId}")
    Order getOrder(@HeaderParam("Authorization") String authorization, @PathParam("orderId") String orderId);

    @POST
    @Path("/{orderId}/refund")
    Order refund(
            @HeaderParam("Authorization") String authorization,
            @HeaderParam("X-Idempotency-Key") String idempotencyKey,
            @PathParam("orderId") String orderId
    );

    @POST
    @Path("/{orderId}/cancel")
    Order cancel(
            @HeaderParam("Authorization") String authorization,
            @HeaderParam("X-Idempotency-Key") String idempotencyKey,
            @PathParam("orderId") String orderId
    );

    /** Valores em string de reais ("90.00"). Nenhum campo de comissão: a plataforma não retém nada. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreateOrderRequest(
            String type,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("processing_mode") String processingMode,
            @JsonProperty("total_amount") String totalAmount,
            String description,
            Payer payer,
            Transactions transactions
    ) {
    }

    /** {@code first_name} só vai no sandbox: "APRO" faz o Mercado Pago aprovar o Pix de teste sozinho. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Payer(String email, @JsonProperty("first_name") String firstName) {
    }

    record Transactions(List<PaymentRequest> payments) {
    }

    record PaymentRequest(
            String amount,
            @JsonProperty("payment_method") PaymentMethodRequest paymentMethod,
            @JsonProperty("expiration_time") String expirationTime
    ) {
    }

    record PaymentMethodRequest(String id, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Order(
            String id,
            String status,
            @JsonProperty("status_detail") String statusDetail,
            OrderTransactions transactions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderTransactions(List<OrderPayment> payments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderPayment(String id, String status, @JsonProperty("payment_method") OrderPaymentMethod paymentMethod) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderPaymentMethod(
            @JsonProperty("qr_code") String qrCode,
            @JsonProperty("qr_code_base64") String qrCodeBase64,
            @JsonProperty("ticket_url") String ticketUrl
    ) {
    }
}
