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
public class RuleGroupDto {

    @NotBlank
    private String logicOperator; // AND | OR

    @Valid
    @Builder.Default
    private List<RuleDto> rules = List.of();

    @Valid
    @Builder.Default
    private List<RuleGroupDto> childGroups = List.of(); // recursive — arbitrary nesting depth
}
