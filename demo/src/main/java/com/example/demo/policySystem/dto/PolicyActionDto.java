package com.example.demo.policySystem.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyActionDto {

    @NotBlank
    private String actionType; // "SET_FIELD" | "ASSIGN_USER" | ... (extensible)

    // used by SET_FIELD
    private String outputParamName;
    private String outputValue;

    // used by handlers needing arbitrary config (e.g. ASSIGN_USER's strategy/queueId)
    @Builder.Default
    private Map<String, String> params = Map.of();
}
