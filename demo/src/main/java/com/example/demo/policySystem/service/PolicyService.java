package com.example.demo.policySystem.service;

import com.example.demo.policySystem.PolicyAssembler;
import com.example.demo.policySystem.cache.PolicyTreeCache;
import com.example.demo.policySystem.domain.Policy;
import com.example.demo.policySystem.domain.RuleGroup;
import com.example.demo.policySystem.dto.*;
import com.example.demo.policySystem.enums.PolicyStatus;
import com.example.demo.policySystem.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // Lombok: generates the constructor for the final fields below
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyAssembler policyAssembler;
    private final PolicyTreeCache policyTreeCache;

    // ================= Create =================

    @Transactional
    public PolicyResponse createPolicy(CreatePolicyRequest request) {
        validate(request.getRootGroup(), request.getActions());

        Policy policy = Policy.builder()
                .name(request.getName())
                .description(request.getDescription())
                .systemCode(request.getSystemCode())
                .orderIndex(request.getOrderIndex())
                .status(PolicyStatus.DRAFT)
                .active(true)
                .build();

        RuleGroup rootGroup = policyAssembler.toEntity(request.getRootGroup(), null, 1);
        policy.setRootGroup(rootGroup);

        request.getActions().forEach(actionDto -> policy.addAction(policyAssembler.toEntity(actionDto, policy)));

        Policy saved = policyRepository.save(policy); // cascades through rootGroup -> children -> rules -> values
        // no cache reload here: a new policy is created as DRAFT and is invisible to
        // evaluation until publish() is called
        return policyAssembler.toResponse(saved);
    }

    // ================= Read =================

    @Transactional(readOnly = true)
    public PolicyResponse getPolicy(Long id) {
        Policy policy = findOrThrow(id);
        return policyAssembler.toResponse(policy);
    }

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> listPolicies(String systemCode) {
        List<Policy> policies = (systemCode != null)
                ? policyRepository.findBySystemCodeOrderByOrderIndexAsc(systemCode)
                : policyRepository.findAllByOrderBySystemCodeAscOrderIndexAsc();
        return policies.stream().map(policyAssembler::toSummary).toList();
    }

    // ================= Update =================

    @Transactional
    public PolicyResponse updatePolicy(Long id, UpdatePolicyRequest request) {
        validate(request.getRootGroup(), request.getActions());

        Policy policy = findOrThrow(id);

        policy.setName(request.getName());
        policy.setDescription(request.getDescription());
        policy.setSystemCode(request.getSystemCode());
        policy.setOrderIndex(request.getOrderIndex());

        // Full tree replacement rather than diffing: orphanRemoval on Policy.rootGroup
        // (OneToOne, CascadeType.ALL, orphanRemoval = true) deletes the old RuleGroup
        // subtree automatically when it's replaced with a new one.
        RuleGroup newRootGroup = policyAssembler.toEntity(request.getRootGroup(), null, 1);
        policy.setRootGroup(newRootGroup);

        policy.getActions().clear(); // orphanRemoval on Policy.actions deletes the old rows
        request.getActions().forEach(actionDto -> policy.addAction(policyAssembler.toEntity(actionDto, policy)));

        // Editing a published policy pulls it back to DRAFT so changes can't go live
        // without an explicit re-publish (and, ideally, a re-test first).
        if (policy.getStatus() == PolicyStatus.PUBLISHED) {
            policy.setStatus(PolicyStatus.DRAFT);
        }

        Policy saved = policyRepository.save(policy);
        policyTreeCache.reload(); // in case this policy was PUBLISHED before, it must drop out of the cache now
        return policyAssembler.toResponse(saved);
    }

    // ================= Delete =================

    @Transactional
    public void deletePolicy(Long id) {
        Policy policy = findOrThrow(id);
        policyRepository.delete(policy); // cascades to rootGroup tree and actions
        policyTreeCache.reload();
    }

    // ================= Lifecycle =================

    @Transactional
    public PolicyResponse publish(Long id) {
        Policy policy = findOrThrow(id);
        policy.setStatus(PolicyStatus.PUBLISHED);
        Policy saved = policyRepository.save(policy);
        policyTreeCache.reload(); // the newly published policy takes effect immediately
        return policyAssembler.toResponse(saved);
    }

    @Transactional
    public PolicyResponse archive(Long id) {
        Policy policy = findOrThrow(id);
        policy.setStatus(PolicyStatus.ARCHIVED);
        Policy saved = policyRepository.save(policy);
        policyTreeCache.reload(); // archived policies must drop out of evaluation immediately
        return policyAssembler.toResponse(saved);
    }

    // ================= Reorder =================

    @Transactional
    public void reorder(ReorderPoliciesRequest request) {
        Map<Long, Integer> targetOrderByPolicyId = request.getOrder().stream()
                .collect(Collectors.toMap(
                        ReorderPoliciesRequest.PolicyOrderEntry::getPolicyId,
                        ReorderPoliciesRequest.PolicyOrderEntry::getOrderIndex));

        List<Policy> policies = policyRepository.findAllById(targetOrderByPolicyId.keySet());

        if (policies.size() != targetOrderByPolicyId.size()) {
            throw new IllegalArgumentException("One or more policy IDs in the reorder request do not exist");
        }

        // Phase 1: push every affected row to a temporary, guaranteed-unique negative
        // index (offset by id, which is already unique) — avoids collisions regardless
        // of what order these updates get issued in.
        for (Policy policy : policies) {
            policy.setOrderIndex((int) -policy.getId());
        }
        policyRepository.saveAll(policies);
        policyRepository.flush();

        // Phase 2: now apply the real target values, looked up by ID — no reliance
        // on list ordering anywhere in this method.
        for (Policy policy : policies) {
            policy.setOrderIndex(targetOrderByPolicyId.get(policy.getId()));
        }
        policyRepository.saveAll(policies);

        policyTreeCache.reload();
    }

    // ================= Helpers =================

    private Policy findOrThrow(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + id));
    }

    private void validate(RuleGroupDto rootGroup, List<PolicyActionDto> actions) {
        if (rootGroup == null || isEmptyGroup(rootGroup)) {
            throw new IllegalArgumentException("Policy has no rules defined");
        }
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("Policy must define at least one output action");
        }
    }

    private boolean isEmptyGroup(RuleGroupDto group) {
        boolean hasRules = group.getRules() != null && !group.getRules().isEmpty();
        boolean hasChildren = group.getChildGroups() != null && !group.getChildGroups().isEmpty();
        return !hasRules && !hasChildren;
    }
}
