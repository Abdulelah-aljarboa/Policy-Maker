package com.example.demo.policySystem.evaluation.tree;

import com.example.demo.policySystem.CaseItemContext;
import com.example.demo.policySystem.enums.LogicOperator;

import java.util.List;

public record GroupNode(LogicOperator operator, List<RuleNode> children) implements RuleNode {
    @Override
    public boolean evaluate(CaseItemContext ctx) {
        return switch (operator) {
            case AND -> children.stream().allMatch(child -> child.evaluate(ctx));
            case OR -> children.stream().anyMatch(child -> child.evaluate(ctx));
        };
    }
}
