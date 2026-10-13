package br.com.stringtracker.service;

import br.com.stringtracker.client.MercadoPagoOAuthClient;
import br.com.stringtracker.client.MercadoPagoOAuthClient.TokenResponse;
import br.com.stringtracker.dto.ConnectUrlResponse;
import br.com.stringtracker.dto.PaymentAccountResponse;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.service.payment.OAuthStateSigner;
import br.com.stringtracker.service.payment.OAuthStateSigner.OAuthState;
import br.com.stringtracker.service.payment.TokenCipher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Conexão da conta Mercado Pago do clube por OAuth. Os tokens ficam cifrados em {@code clubs}
 * e nenhuma resposta da API os devolve.
 */
@ApplicationScoped
public class PaymentAccountService {

    @Inject
    ClubAccessService access;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ClubRepository clubRepository;

    @Inject
    OAuthStateSigner stateSigner;

    @Inject
    TokenCipher tokenCipher;

    @Inject
    @RestClient
    MercadoPagoOAuthClient oauthClient;

    @ConfigProperty(name = "mp.oauth.base-url")
    String oauthBaseUrl;

    @ConfigProperty(name = "mp.client-id")
    String clientId;

    @ConfigProperty(name = "mp.client-secret")
    String clientSecret;

    @ConfigProperty(name = "mp.redirect-uri")
    String redirectUri;

    @Transactional
    public ConnectUrlResponse connectUrl(long clubId) {
        access.requireClubAdmin(clubId);
        requireClub(clubId);
        String state = stateSigner.issue(clubId, currentUserService.requireCurrentUser().getId(), Instant.now());
        return new ConnectUrlResponse(oauthBaseUrl + "/authorization"
                + "?client_id=" + encode(clientId)
                + "&response_type=code&platform_id=mp"
                + "&state=" + encode(state)
                + "&redirect_uri=" + encode(redirectUri));
    }

    @Transactional
    public PaymentAccountResponse connect(String code, String state) {
        User user = currentUserService.requireCurrentUser();
        OAuthState verified = stateSigner.verify(state, Instant.now());
        // O state só vale para quem iniciou a conexão e ainda administra o clube.
        if (verified.userId() != user.getId() || !access.adminClubIds().contains(verified.clubId())) {
            throw new BadRequestException("State do OAuth inválido ou expirado");
        }
        Club club = requireClub(verified.clubId());

        TokenResponse tokens = exchange(code);
        club.setMpUserId(String.valueOf(tokens.userId()));
        club.setMpAccessTokenEnc(tokenCipher.encrypt(tokens.accessToken()));
        club.setMpRefreshTokenEnc(tokenCipher.encrypt(tokens.refreshToken()));
        club.setMpTokenExpiresAt(Instant.now().plusSeconds(tokens.expiresIn()));
        club.setPaymentStatus(ClubPaymentStatus.CONNECTED);
        return new PaymentAccountResponse(club.getPaymentStatus());
    }

    private TokenResponse exchange(String code) {
        try {
            return oauthClient.exchangeCode("authorization_code", clientId, clientSecret, code, redirectUri);
        } catch (WebApplicationException | ProcessingException e) {
            throw new PaymentProviderException("Não foi possível conectar ao Mercado Pago. Tente novamente", e);
        }
    }

    private Club requireClub(long clubId) {
        return clubRepository.findActiveById(clubId)
                .orElseThrow(() -> new NotFoundException("Clube não encontrado"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
