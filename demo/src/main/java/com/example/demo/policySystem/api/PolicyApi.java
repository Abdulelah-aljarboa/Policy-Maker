package com.example.demo.policySystem.api;

import com.example.demo.policySystem.dto.*;
import com.example.demo.policySystem.service.PolicyService;
import com.example.demo.policySystem.service.PolicyTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyApi {

    private final PolicyService policyService;
    private final PolicyTestService policyTestService;

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody CreatePolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.createPolicy(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(policyService.getPolicy(id));
    }

    @GetMapping
    public ResponseEntity<List<PolicySummaryResponse>> list(
            @RequestParam(required = false) String systemCode) {
        return ResponseEntity.ok(policyService.listPolicies(systemCode));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PolicyResponse> update(@PathVariable Long id, @Valid @RequestBody UpdatePolicyRequest request) {
        return ResponseEntity.ok(policyService.updatePolicy(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        policyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<PolicyResponse> publish(@PathVariable Long id) {
        return ResponseEntity.ok(policyService.publish(id));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<PolicyResponse> archive(@PathVariable Long id) {
        return ResponseEntity.ok(policyService.archive(id));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderPoliciesRequest request) {
        policyService.reorder(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<TestPolicyResponse> test(@PathVariable Long id, @Valid @RequestBody TestPolicyRequest request) {
        return ResponseEntity.ok(policyTestService.testPolicy(id, request));
    }
}
