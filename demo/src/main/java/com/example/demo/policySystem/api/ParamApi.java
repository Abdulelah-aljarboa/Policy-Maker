package com.example.demo.policySystem.api;

import com.example.demo.policySystem.PolicyAssembler;
import com.example.demo.policySystem.dto.ParamMetaDto;
import com.example.demo.policySystem.repository.ParamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/params")
@RequiredArgsConstructor
public class ParamApi {

    private final ParamRepository paramRepository;
    private final PolicyAssembler policyAssembler;

    @GetMapping
    public List<ParamMetaDto> listParams() {
        return paramRepository.findAll().stream().map(policyAssembler::toDto).toList();
    }
}
