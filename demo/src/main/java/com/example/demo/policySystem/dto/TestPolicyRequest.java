package com.example.demo.policySystem.dto;

import lombok.*;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

// POST /api/policies/{id}/test body — sample field values keyed by entity, not a
// real CaseItemEntity. Lets a user try a DRAFT policy before publishing it.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestPolicyRequest {

    @NotNull
    @Builder.Default
    private Map<String, Object> establishment = Map.of();

    @NotNull
    @Builder.Default
    private Map<String, Object> person = Map.of();

    @NotNull
    @Builder.Default
    private Map<String, Object> engagement = Map.of();
}
