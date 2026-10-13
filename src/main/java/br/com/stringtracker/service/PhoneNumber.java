package br.com.stringtracker.service;

import jakarta.ws.rs.BadRequestException;

import java.util.regex.Pattern;

/** Telefone e WhatsApp são guardados só com os dígitos, o formato que o link wa.me aceita. */
public final class PhoneNumber {

    private static final Pattern FORMAT = Pattern.compile("^\\+?[\\d\\s().-]+$");
    private static final int MIN_DIGITS = 10;
    private static final int MAX_DIGITS = 15;

    private PhoneNumber() {
    }

    /** Os dígitos do número; 400 com {@code invalidMessage} se não for um telefone de 10 a 15 dígitos. */
    public static String digitsOf(String raw, String invalidMessage) {
        String trimmed = raw == null ? "" : raw.trim();
        String digits = trimmed.replaceAll("\\D", "");
        if (!FORMAT.matcher(trimmed).matches() || digits.length() < MIN_DIGITS || digits.length() > MAX_DIGITS) {
            throw new BadRequestException(invalidMessage);
        }
        return digits;
    }
}
