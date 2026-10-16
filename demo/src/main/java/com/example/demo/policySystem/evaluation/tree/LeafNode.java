package com.example.demo.policySystem.evaluation.tree;

import com.example.demo.policySystem.CaseItemContext;
import com.example.demo.policySystem.enums.ParamType;
import com.example.demo.policySystem.enums.ValueType;
import com.example.demo.policySystem.evaluation.OperatorEvaluator;

import java.util.List;

public record LeafNode(
        String entityName,
        String paramName,
        ParamType paramType,
        String operator,
        ValueType valueType,
        List<Object> literalValues,
        String refEntityName,
        String refParamName,
        boolean negate
) implements RuleNode {

    @Override
    public boolean evaluate(CaseItemContext ctx) {
        Object actual = ctx.resolve(entityName, paramName);
        Object comparisonTarget = valueType == ValueType.PARAM_REF
                ? ctx.resolve(refEntityName, refParamName)
                : (literalValues.size() == 1 ? literalValues.get(0) : literalValues);

        boolean result = OperatorEvaluator.apply(operator, actual, comparisonTarget, paramType);
        return negate != result;
    }
}
