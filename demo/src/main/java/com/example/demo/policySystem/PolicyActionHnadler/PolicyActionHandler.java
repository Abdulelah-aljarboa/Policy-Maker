package com.example.demo.policySystem.PolicyActionHnadler;

import com.example.demo.policySystem.domain.PolicyAction;

public interface PolicyActionHandler {
    String getActionType(); // "SET_FIELD", "ASSIGN_USER", etc. — used for registry lookup

    void apply(CaseItemEntity caseItem, PolicyAction action);
}
