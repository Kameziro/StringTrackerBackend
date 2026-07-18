package br.com.stringtracker.dto;

import br.com.stringtracker.model.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateGroupMemberRoleRequest {

    @NotNull
    private GroupRole role;
}
