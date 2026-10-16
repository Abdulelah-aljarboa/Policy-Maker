package com.example.demo.policySystem;

import com.example.demo.policySystem.PolicyActionHnadler.PolicyActionHandler;
import com.example.demo.policySystem.domain.PolicyAction;
import com.example.demo.policySystem.exception.RuleEvaluationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PolicyActionApplier {

    private final Map<String, PolicyActionHandler> handlersByType;

    // Spring autowires all PolicyActionHandler beans — adding a new action type
    // later is just "write a new @Component implementing the interface," zero
    // changes needed here
    public PolicyActionApplier(List<PolicyActionHandler> handlers) {
        this.handlersByType = handlers.stream()
                .collect(Collectors.toMap(PolicyActionHandler::getActionType, h -> h));
    }

    public void applyActions(CaseItemEntity caseItem, List<PolicyAction> actions) {
        for (PolicyAction action : actions) {
            PolicyActionHandler handler = handlersByType.get(action.getActionType());
            if (handler == null) {
                throw new RuleEvaluationException("Unknown action type: " + action.getActionType());
            }
            handler.apply(caseItem, action);
        }
    }
}
