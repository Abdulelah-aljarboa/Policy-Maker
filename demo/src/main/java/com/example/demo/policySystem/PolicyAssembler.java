package com.example.demo.policySystem;

import com.example.demo.policySystem.domain.*;
import com.example.demo.policySystem.dto.*;
import com.example.demo.policySystem.enums.LogicOperator;
import com.example.demo.policySystem.enums.ValueType;
import com.example.demo.policySystem.repository.ParamOperatorRepository;
import com.example.demo.policySystem.repository.ParamRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts between the wire-level DTOs and the JPA entity tree, in both
 * directions. All recursion (RuleGroupDto <-> RuleGroup) lives here so
 * PolicyService and PolicyController stay free of tree-walking logic.
 */
@Component
public class PolicyAssembler {

    private final ParamRepository paramRepository;
    private final ParamOperatorRepository paramOperatorRepository;

    public PolicyAssembler(ParamRepository paramRepository, ParamOperatorRepository paramOperatorRepository) {
        this.paramRepository = paramRepository;
        this.paramOperatorRepository = paramOperatorRepository;
    }

    // ================= DTO -> Entity =================

    /** Builds a detached RuleGroup tree (with parent links set) ready to be assigned as a Policy's rootGroup. */
    public RuleGroup toEntity(RuleGroupDto dto, RuleGroup parent, int orderIndex) {
        RuleGroup group = new RuleGroup();
        group.setLogicOperator(LogicOperator.valueOf(dto.getLogicOperator()));
        group.setParent(parent);
        group.setOrderIndex(orderIndex);

        List<RuleGroup> childGroups = new ArrayList<>();
        int i = 1;
        for (RuleGroupDto childDto : dto.getChildGroups()) {
            RuleGroup child = toEntity(childDto, group, i++); // recurse
            childGroups.add(child);
        }
        group.setChildGroups(childGroups);

        List<Rule> rules = new ArrayList<>();
        int j = 1;
        for (RuleDto ruleDto : dto.getRules()) {
            rules.add(toEntity(ruleDto, group, j++));
        }
        group.setRules(rules);

        return group;
    }

    public Rule toEntity(RuleDto dto, RuleGroup group, int orderIndex) {
        Param param = paramRepository.findByEntityNameAndParamName(dto.getEntityName(), dto.getParamName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown param: " + dto.getEntityName() + "." + dto.getParamName()));

        boolean operatorAllowed = paramOperatorRepository.existsByParamIdAndOperator(param.getId(), dto.getOperator());
        if (!operatorAllowed) {
            throw new IllegalArgumentException(
                    "Operator " + dto.getOperator() + " is not allowed for " + dto.getParamName());
        }

        Rule rule = new Rule();
        rule.setGroup(group);
        rule.setParam(param);
        rule.setOperator(dto.getOperator());
        rule.setValueType(ValueType.valueOf(dto.getValueType()));
        rule.setNegate(Boolean.TRUE.equals(dto.getNegate()));
        rule.setOrderIndex(orderIndex);

        if (rule.getValueType() == ValueType.PARAM_REF) {
            Param refParam = paramRepository.findByEntityNameAndParamName(dto.getRefEntityName(), dto.getRefParamName())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown ref param"));
            rule.setRefParam(refParam);
        } else {
            List<RuleValue> values = dto.getValues().stream()
                    .map(v -> {
                        RuleValue rv = new RuleValue();
                        rv.setRule(rule);
                        rv.setValue(v);
                        return rv;
                    })
                    .toList();
            rule.setValues(values);
        }

        return rule;
    }

    public PolicyAction toEntity(PolicyActionDto dto, Policy policy) {
        PolicyAction action = new PolicyAction();
        action.setPolicy(policy);
        action.setActionType(dto.getActionType());
        action.setOutputParamName(dto.getOutputParamName());
        action.setOutputValue(dto.getOutputValue());

        dto.getParams().forEach((key, value) -> action.addParam(key, value));
        return action;
    }

    // ================= Entity -> DTO =================

    public RuleGroupDto toDto(RuleGroup group) {
        return RuleGroupDto.builder()
                .logicOperator(group.getLogicOperator().name())
                .childGroups(group.getChildGroups().stream().map(this::toDto).toList()) // recurse
                .rules(group.getRules().stream().map(this::toDto).toList())
                .build();
    }

    public RuleDto toDto(Rule rule) {
        return RuleDto.builder()
                .entityName(rule.getParam().getEntityName())
                .paramName(rule.getParam().getParamName())
                .operator(rule.getOperator())
                .valueType(rule.getValueType().name())
                .values(rule.getValues().stream().map(RuleValue::getValue).toList())
                .refEntityName(rule.getRefParam() != null ? rule.getRefParam().getEntityName() : null)
                .refParamName(rule.getRefParam() != null ? rule.getRefParam().getParamName() : null)
                .negate(rule.getNegate())
                .build();
    }

    public PolicyActionDto toDto(PolicyAction action) {
        return PolicyActionDto.builder()
                .actionType(action.getActionType())
                .outputParamName(action.getOutputParamName())
                .outputValue(action.getOutputValue())
                .params(action.getParams().stream()
                        .collect(Collectors.toMap(
                                PolicyActionParam::getParamKey,
                                PolicyActionParam::getParamValue)))
                .build();
    }

    public PolicyResponse toResponse(Policy policy) {
        return PolicyResponse.builder()
                .id(policy.getId())
                .name(policy.getName())
                .description(policy.getDescription())
                .systemCode(policy.getSystemCode())
                .status(policy.getStatus().name())
                .orderIndex(policy.getOrderIndex())
                .active(policy.getActive())
                .rootGroup(toDto(policy.getRootGroup()))
                .actions(policy.getActions().stream().map(this::toDto).toList())
                .build();
    }

    public PolicySummaryResponse toSummary(Policy policy) {
        return PolicySummaryResponse.builder()
                .id(policy.getId())
                .name(policy.getName())
                .systemCode(policy.getSystemCode())
                .status(policy.getStatus().name())
                .orderIndex(policy.getOrderIndex())
                .active(policy.getActive())
                .build();
    }

    public ParamMetaDto toDto(Param param) {
        return ParamMetaDto.builder()
                .id(param.getId())
                .entityName(param.getEntityName())
                .paramName(param.getParamName())
                .paramType(param.getParamType().name())
                .allowedOperators(param.getAllowedOperators().stream()
                        .map(ParamOperator::getOperator)
                        .toList())
                .build();
    }
}
