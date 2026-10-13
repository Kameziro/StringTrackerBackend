package br.com.stringtracker.service.payment;

import br.com.stringtracker.client.MercadoPagoClient;
import br.com.stringtracker.client.MercadoPagoClient.CreateOrderRequest;
import br.com.stringtracker.client.MercadoPagoClient.Order;
import br.com.stringtracker.client.MercadoPagoClient.OrderPayment;
import br.com.stringtracker.client.MercadoPagoClient.OrderPaymentMethod;
import br.com.stringtracker.client.MercadoPagoClient.OrderTransactions;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Com MP_SANDBOX_PAYER_FIRST_NAME definido (só em dev), o Pix vai com o nome que o sandbox usa para decidir o resultado. */
@QuarkusTest
@TestProfile(MercadoPagoGatewaySandboxTest.SandboxPayer.class)
class MercadoPagoGatewaySandboxTest {

    public static class SandboxPayer implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("mp.sandbox.payer-first-name", "APRO");
        }
    }

    @InjectMock
    @RestClient
    MercadoPagoClient client;

    @Inject
    MercadoPagoGateway gateway;

    @Inject
    ObjectMapper json;

    @Test
    void createPix_sendsTheSandboxFirstNameWithThePayerEmail() throws Exception {
        when(client.createOrder(anyString(), anyString(), any())).thenReturn(new Order("ORD1", "action_required",
                "waiting_transfer", new OrderTransactions(List.of(new OrderPayment("PAY1", "action_required",
                new OrderPaymentMethod("00020126", "base64-qr", "https://mp.example/ticket"))))));

        gateway.createPix(new ClubCredentials("club-token"), 9000, "Aula de padel", "aluno@example.com", "booking-7",
                Duration.ofMinutes(10), "booking-7");

        ArgumentCaptor<CreateOrderRequest> request = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(client).createOrder(eq("Bearer club-token"), eq("booking-7"), request.capture());
        JsonNode payer = json.readTree(json.writeValueAsString(request.getValue())).get("payer");
        assertEquals("aluno@example.com", payer.get("email").asText());
        assertEquals("APRO", payer.get("first_name").asText());
    }
}
