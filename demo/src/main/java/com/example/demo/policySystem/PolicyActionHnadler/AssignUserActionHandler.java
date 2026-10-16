package com.example.demo.policySystem.PolicyActionHnadler;

import com.example.demo.policySystem.domain.PolicyAction;
import org.springframework.stereotype.Component;

@Component
public class AssignUserActionHandler implements PolicyActionHandler {

    @Override
    public String getActionType() {
        return "ASSIGN_USER";
    }

    @Override
    public void apply(CaseItemEntity caseItem, PolicyAction action) {
        String strategy = action.getParam("strategy").orElse("ROUND_ROBIN");
        String queueId = action.getParam("queueId").orElse(null);

        User assignee = userAssignmentService.pickUser(strategy, queueId, caseItem);
        caseItem.setAssignedUserId(assignee.getId());
        // whatever else "assign to a user" means in your system —
        // e.g. creating a CaseWorkItem, notifying them, setting a status
    }
}
