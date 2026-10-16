package com.example.demo.policySystem.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestPolicyResponse {

    private boolean matched;

    @Builder.Default
    private List<PolicyActionDto> resultingActions = List.of(); // empty if not matched

    private String evaluationError; // populated only if the rule config itself is broken
}
