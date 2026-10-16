package com.example.demo.policySystem.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleDto {

    @NotBlank
    private String entityName;        // ESTABLISHMENT | PERSON | ENGAGEMENT

    @NotBlank
    private String paramName;

    @NotBlank
    private String operator;          // '=', '>', 'IN', 'BETWEEN', ...

    @NotNull
    @Builder.Default
    private String valueType = "LITERAL"; // LITERAL | PARAM_REF

    @Builder.Default
    private List<String> values = List.of(); // used when valueType = LITERAL

    private String refEntityName;     // used when valueType = PARAM_REF
    private String refParamName;      // used when valueType = PARAM_REF

    @Builder.Default
    private Boolean negate = false;
}
