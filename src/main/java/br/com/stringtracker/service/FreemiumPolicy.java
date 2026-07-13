package br.com.stringtracker.service;

import br.com.stringtracker.model.User;

public final class FreemiumPolicy {

    public static final String FREE_TIER_LIMIT_MESSAGE =
            "Limite de raquetes atingido para usuários gratuitos. Faça o upgrade para o Premium!";

    private FreemiumPolicy() {
    }

    public static void assertCanCreateRacket(User user, long ownedRacketCount) {
        if (!user.isPremium() && ownedRacketCount >= 1) {
            throw new FreeTierLimitExceededException();
        }
    }
}
