package com.example.demo.policySystem;

import com.example.demo.policySystem.exception.RuleEvaluationException;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.PropertyAccessorFactory;

public class CaseItemContext {
    private final CaseItemEntity caseItem;
    private final EstablishmentEnrichment establishment;
    private final PersonEnrichment person;
    private final EngagementEnrichment engagement;

    public Object resolve(String entityName, String paramName) throws RuleEvaluationException {
        Object target = switch (entityName) {
            case "ESTABLISHMENT" -> establishment;
            case "PERSON" -> person;
            case "ENGAGEMENT" -> engagement;
            default -> throw new RuleEvaluationException("Unknown entity: " + entityName);
        };

        try {
            return PropertyAccessorFactory.forBeanPropertyAccess(target)
                    .getPropertyValue(paramName);
        } catch (InvalidPropertyException e) {
            throw new RuleEvaluationException("Unknown param: " + entityName + "." + paramName + " — check the Param table matches the entity's getters \n", e);
        }
    }
}
