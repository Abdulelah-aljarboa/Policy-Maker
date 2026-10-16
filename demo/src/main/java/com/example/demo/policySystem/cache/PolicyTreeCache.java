package com.example.demo.policySystem.cache;

import com.example.demo.policySystem.domain.Policy;
import com.example.demo.policySystem.enums.PolicyStatus;
import com.example.demo.policySystem.evaluation.PolicyTreeBuilder;
import com.example.demo.policySystem.evaluation.tree.PolicyTree;
import com.example.demo.policySystem.repository.PolicyRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PolicyTreeCache {

    @Autowired
    private PolicyTreeBuilder policyTreeBuilder;
    @Autowired
    private PolicyRepository policyRepository;

    private volatile Map<String, List<PolicyTree>> cachedTreesBySystem = Map.of();

    @PostConstruct
    public void init(){
        reload();
    }

    public synchronized void reload() {
        List<Policy> policies = policyRepository.findByStatusAndActiveTrueOrderBySystemCodeAscOrderIndexAsc(PolicyStatus.PUBLISHED);
        this.cachedTreesBySystem = policies.stream()
                .map(p -> new PolicyTree(p, policyTreeBuilder.buildTree(p.getRootGroup())))
                .collect(Collectors.groupingBy(pt -> pt.policy().getSystemCode()));
    }

    public List<PolicyTree> getPoliciesInOrder(String systemCode) {
        return cachedTreesBySystem.getOrDefault(systemCode, List.of());
    }

}

