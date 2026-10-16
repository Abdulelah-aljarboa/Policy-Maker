package com.example.demo.policySystem.dto;

import lombok.*;

// Lightweight shape for GET /api/policies (list view) — no rule tree, since a list
// screen doesn't need it and building/serializing the full tree for every row would
// be wasted work. Use GET /api/policies/{id} to fetch the full PolicyResponse.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicySummaryResponse {

    private Long id;
    private String name;
    private String systemCode;
    private String status; // DRAFT | PUBLISHED | ARCHIVED
    private Integer orderIndex;
    private Boolean active;
}
