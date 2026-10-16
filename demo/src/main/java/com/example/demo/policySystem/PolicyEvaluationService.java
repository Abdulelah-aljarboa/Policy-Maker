package com.example.demo.policySystem;

import com.example.demo.policySystem.cache.PolicyTreeCache;
import com.example.demo.policySystem.domain.Policy;
import com.example.demo.policySystem.evaluation.tree.PolicyTree;
import com.example.demo.policySystem.exception.RuleEvaluationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class PolicyEvaluationService {

    @Autowired
    private PolicyTreeCache treeCache;

    @Autowired
    private PolicyActionApplier policyActionApplier;

    public void process(List<CaseItem> caseItemList) {
        for (CaseItemEntity caseItem : caseItemList) {
            CaseItemContext ctx = buildContext(caseItem);

            Optional<Policy> matchedPolicy = evaluateFirstMatch("TRIAGE" ,ctx);
            matchedPolicy.ifPresent(policy -> policyActionApplier.applyActions(caseItem, policy.getActions()));
        }
    }


    public Optional<Policy> evaluateFirstMatch(String systemCode, CaseItemContext ctx) {
        for (PolicyTree pt : treeCache.getPoliciesInOrder(systemCode)) {
            try {
                if (pt.rootNode().evaluate(ctx)) {
                    return Optional.of(pt.policy());
                }
            } catch (RuleEvaluationException e) {
                log.error("Policy '{}' (id={}) has a misconfigured rule, skipping: {}",
                        pt.policy().getName(), pt.policy().getId(), e.getMessage());
            }
        }
        return Optional.empty();
    }

    private CaseItemContext buildContext(CaseItemEntity caseItem) { /* load enrichments */ }
}
