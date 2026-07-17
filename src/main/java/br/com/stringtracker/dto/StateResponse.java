package br.com.stringtracker.dto;

import br.com.stringtracker.model.State;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StateResponse {

    private Long id;
    private String name;
    private String uf;

    public static StateResponse from(State state) {
        return new StateResponse(state.getId(), state.getName(), state.getUf());
    }
}
