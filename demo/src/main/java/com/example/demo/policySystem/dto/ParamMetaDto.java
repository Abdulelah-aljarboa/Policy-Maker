package com.example.demo.policySystem.dto;

import lombok.*;
import java.util.List;

// Backs GET /api/params — drives every Entity/Param/Operator dropdown in the UI's
// rule builder. One entry per Param row, with its allowed operators embedded so the
// frontend doesn't need a second round trip per param selection.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParamMetaDto {

    private Long id;
    private String entityName;   // ESTABLISHMENT | PERSON | ENGAGEMENT
    private String paramName;
    private String paramType;    // STRING | NUMBER | DATE | BOOLEAN
    private List<String> allowedOperators;
}
