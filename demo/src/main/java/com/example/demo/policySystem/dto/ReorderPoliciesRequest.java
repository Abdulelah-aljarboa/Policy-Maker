package com.example.demo.policySystem.dto;

import lombok.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import java.util.List;

// PATCH /api/policies/reorder body — bulk order_index update, no tree resend needed.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderPoliciesRequest {

    @NotEmpty
    @Valid
    private List<PolicyOrderEntry> order;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PolicyOrderEntry {
        private Long policyId;
        private Integer orderIndex;
    }
}
