package com.example.demo.policySystem.evaluation;

import com.example.demo.policySystem.domain.Rule;
import com.example.demo.policySystem.domain.RuleGroup;
import com.example.demo.policySystem.enums.ValueType;
import com.example.demo.policySystem.evaluation.tree.GroupNode;
import com.example.demo.policySystem.evaluation.tree.LeafNode;
import com.example.demo.policySystem.evaluation.tree.RuleNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PolicyTreeBuilder {

    public RuleNode buildTree(RuleGroup dbGroup) {
        List<RuleNode> children = new ArrayList<>();

        for (RuleGroup childGroup : dbGroup.getChildGroups()) {
            children.add(buildTree(childGroup));
        }

        for  (Rule rule : dbGroup.getRules()) {
            children.add(toLeafNode(rule));
        }

        return new GroupNode(
                dbGroup.getLogicOperator(),
                children
        );
    }

    private LeafNode toLeafNode(Rule rule) {
        List<Object> coercedValues = rule.getValues().stream()
                .map(rv -> ValueCoercer.coerce(rv.getValue(), rule.getParam().getParamType()))
                .toList();

        return new LeafNode(
                rule.getParam().getEntityName(),
                rule.getParam().getParamName(),
                rule.getParam().getParamType(),
                rule.getOperator(),
                rule.getValueType(),
                coercedValues,
                rule.getValueType() == ValueType.PARAM_REF ? rule.getRefParam().getEntityName() : null,
                rule.getValueType() == ValueType.PARAM_REF ? rule.getRefParam().getParamName() : null,
                Boolean.TRUE.equals(rule.getNegate())
        );
    }
}
