package com.example.demo.policySystem.dto;

import lombok.*;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyResponse {

    private Long id;
    private String name;
    private String description;
    private String systemCode;
    private String status; // DRAFT | PUBLISHED | ARCHIVED
    private Integer orderIndex;
    private Boolean active;
    private RuleGroupDto rootGroup;
    private List<PolicyActionDto> actions;
}
