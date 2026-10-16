package com.example.demo.policySystem.PolicyActionHnadler;

import com.example.demo.policySystem.domain.PolicyAction;
import com.example.demo.policySystem.exception.RuleEvaluationException;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.stereotype.Component;

@Component
public class SetFieldActionHandler implements PolicyActionHandler {

    @Override
    public String getActionType() {
        return "SET_FIELD";
    }

    @Override
    public void apply(CaseItemEntity caseItem, PolicyAction action) {
        var accessor = PropertyAccessorFactory.forBeanPropertyAccess(request);
        try {
            accessor.setPropertyValue(action.getOutputParamName(), action.getOutputValue());
        } catch (InvalidPropertyException | TypeMismatchException e) {
            throw new RuleEvaluationException(
                    "Invalid SET_FIELD action: " + action.getOutputParamName()
                            + "=" + action.getOutputValue(), e);
        }
    }
}
