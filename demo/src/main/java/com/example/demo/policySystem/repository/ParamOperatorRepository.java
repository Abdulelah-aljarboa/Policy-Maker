package com.example.demo.policySystem.repository;

import com.example.demo.policySystem.domain.ParamOperator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParamOperatorRepository extends JpaRepository<ParamOperator, Long> {
    boolean existsByParamIdAndOperator(Long paramId, String operator);
}
