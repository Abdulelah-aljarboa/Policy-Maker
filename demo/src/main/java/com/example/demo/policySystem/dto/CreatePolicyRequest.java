package com.example.demo.policySystem.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePolicyRequest {

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
}
