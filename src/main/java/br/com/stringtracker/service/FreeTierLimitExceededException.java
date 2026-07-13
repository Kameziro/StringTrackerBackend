package br.com.stringtracker.service;

public class FreeTierLimitExceededException extends RuntimeException {

    public FreeTierLimitExceededException() {
        super(FreemiumPolicy.FREE_TIER_LIMIT_MESSAGE);
    }
}
