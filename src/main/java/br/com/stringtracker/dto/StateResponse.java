package br.com.stringtracker.dto;

import br.com.stringtracker.model.State;

public record StateResponse(Long id, String name, String uf) {
    public static StateResponse from(State state) {
        return new StateResponse(state.getId(), state.getName(), state.getUf());
    }
}
