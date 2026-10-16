package com.example.demo.policySystem.repository;

import com.example.demo.policySystem.domain.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ParamRepository extends JpaRepository<Param, Long> {

    Optional<Param> findByEntityNameAndParamName(String entityName, String paramName);

    List<Param> findAll(); // used for GET /api/params, eager-fetch operators via entity graph or join fetch
}
