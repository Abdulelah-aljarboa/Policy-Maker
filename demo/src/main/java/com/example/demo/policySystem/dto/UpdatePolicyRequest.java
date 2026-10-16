package com.example.demo.policySystem.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;

// Same shape as CreatePolicyRequest. Kept as a separate type (rather than reusing
// CreatePolicyRequest) because update semantics may diverge over time (e.g. adding
// an optimistic-locking version field) without forcing a change to the create contract.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePolicyRequest {

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String systemCode;

    @NotNull
    private Integer orderIndex;

    @NotNull
    @Valid
    private RuleGroupDto rootGroup;

    @NotEmpty
    @Valid
    private List<PolicyActionDto> actions;

    // Status is intentionally NOT settable here — publish/unpublish/archive go through
    // their own dedicated endpoints (see PolicyApi) so status transitions stay
    // auditable and can't be smuggled in through an unrelated "edit the rules" call.
}
