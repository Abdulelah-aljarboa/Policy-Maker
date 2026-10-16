package com.example.demo.policySystem.service;

import com.example.demo.policySystem.CaseItemContext;
import com.example.demo.policySystem.PolicyAssembler;
import com.example.demo.policySystem.domain.Policy;
import com.example.demo.policySystem.dto.PolicyActionDto;
import com.example.demo.policySystem.dto.TestPolicyRequest;
import com.example.demo.policySystem.dto.TestPolicyResponse;
import com.example.demo.policySystem.evaluation.PolicyTreeBuilder;
import com.example.demo.policySystem.evaluation.tree.RuleNode;
import com.example.demo.policySystem.exception.RuleEvaluationException;
import com.example.demo.policySystem.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Evaluates a policy (typically still DRAFT) against user-supplied sample data,
 * without going through PolicyTreeCache. A draft policy is never cached — this
 * builds its tree fresh every call, reusing the exact same PolicyTreeBuilder and
 * RuleNode.evaluate() the production path uses, so there's no risk of the test
 * result disagreeing with what happens once the policy is actually published.
 */
@Service
@RequiredArgsConstructor
public class PolicyTestService {

    private final PolicyRepository policyRepository;
    private final PolicyTreeBuilder treeBuilder;
    private final PolicyAssembler policyAssembler;

    @Transactional(readOnly = true)
    public TestPolicyResponse testPolicy(Long policyId, TestPolicyRequest request) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        RuleNode tree = treeBuilder.buildTree(policy.getRootGroup());
        CaseItemContext ctx = CaseItemContext.fromRawMaps(
                request.getEstablishment(), request.getPerson(), request.getEngagement());

        try {
            boolean matched = tree.evaluate(ctx);
            List<PolicyActionDto> actions = matched
                    ? policy.getActions().stream().map(policyAssembler::toDto).toList()
                    : List.of();

            return TestPolicyResponse.builder()
                    .matched(matched)
                    .resultingActions(actions)
                    .build();
        } catch (RuleEvaluationException e) {
            return TestPolicyResponse.builder()
                    .matched(false)
                    .resultingActions(List.of())
                    .evaluationError(e.getMessage())
                    .build();
        }
    }
}
