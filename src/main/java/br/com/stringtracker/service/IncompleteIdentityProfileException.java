package br.com.stringtracker.service;

/**
 * Controlo interno: password grant falhou porque o perfil no Keycloak está incompleto
 * (ex.: falta lastName com VERIFY_PROFILE). Não deve chegar ao cliente HTTP.
 */
class IncompleteIdentityProfileException extends RuntimeException {
}
