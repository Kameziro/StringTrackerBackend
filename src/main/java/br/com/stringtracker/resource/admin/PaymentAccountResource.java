package br.com.stringtracker.resource.admin;

import br.com.stringtracker.dto.PaymentAccountResponse;
import br.com.stringtracker.service.PaymentAccountService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * O provedor redireciona o admin para o painel, que repassa {@code code} e {@code state} ao
 * callback com o Bearer dele; por isso o callback é autenticado e confere o admin do state.
 * A rota {@code connect-url} fica em {@link ClubAdminResource}: um {@code @Path("/api/admin")} aqui
 * perderia a seleção de classe para {@code /api/admin/clubs/{clubId}} e responderia 404.
 */
@Path("/api/admin/payment-account")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class PaymentAccountResource {

    @Inject
    PaymentAccountService paymentAccountService;

    @GET
    @Path("/callback")
    public PaymentAccountResponse callback(
            @QueryParam("code") @NotBlank String code,
            @QueryParam("state") @NotBlank String state
    ) {
        return paymentAccountService.connect(code, state);
    }
}
