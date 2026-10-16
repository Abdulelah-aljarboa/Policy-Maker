package com.example.demo.policySystem.evaluation.tree;

import com.example.demo.policySystem.domain.Policy;

public record PolicyTree(Policy policy, RuleNode rootNode) {}
