package com.example.demo.policySystem.evaluation.tree;

import com.example.demo.policySystem.CaseItemContext;

public sealed interface RuleNode permits GroupNode, LeafNode {
    boolean evaluate(CaseItemContext ctx);
}
