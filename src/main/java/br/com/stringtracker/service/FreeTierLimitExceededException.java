package br.com.stringtracker.service;

/**
 * Thrown when a free-tier user already owns the maximum number of rackets.
 */
public class FreeTierLimitExceededException extends RuntimeException {

    public FreeTierLimitExceededException() {
        super(FreemiumPolicy.FREE_TIER_LIMIT_MESSAGE);
    }
}
