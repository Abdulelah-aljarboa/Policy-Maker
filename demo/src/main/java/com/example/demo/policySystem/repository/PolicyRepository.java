package com.example.demo.policySystem.repository;

import com.example.demo.policySystem.domain.Policy;
import com.example.demo.policySystem.enums.PolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    List<Policy> findByStatusAndActiveTrueOrderBySystemCodeAscOrderIndexAsc(PolicyStatus status);

    List<Policy> findAllByOrderBySystemCodeAscOrderIndexAsc();

    List<Policy> findBySystemCodeOrderByOrderIndexAsc(String systemCode);

    boolean existsBySystemCodeAndOrderIndex(String systemCode, Integer orderIndex);
}
