package br.com.stringtracker.service;

/** Clube ou professor inexistente ou inativo, aberto por um link ou pela lista (DISC-04). Vira HTTP 404 com o código LINK_UNAVAILABLE. */
public class LinkUnavailableException extends RuntimeException {

    public static final String CODE = "LINK_UNAVAILABLE";

    public LinkUnavailableException() {
        super("Este link não está mais disponível");
    }
}
