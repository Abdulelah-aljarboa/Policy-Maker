package com.example.demo.policySystem.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "policy_action")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"policy", "params"})
public class PolicyAction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "policy_action_seq")
    @SequenceGenerator(name = "policy_action_seq", sequenceName = "policy_action_id_seq", allocationSize = 20)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType; // "SET_FIELD" | "ASSIGN_USER" | ... (extensible)

    @Column(name = "output_param_name", length = 100)
    private String outputParamName; // used by SET_FIELD

    @Column(name = "output_value", length = 500)
    private String outputValue; // used by SET_FIELD

    @OneToMany(mappedBy = "action", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PolicyActionParam> params = new ArrayList<>();

    public Optional<String> getParam(String key) {
        return params.stream()
                .filter(p -> p.getParamKey().equals(key))
                .map(PolicyActionParam::getParamValue)
                .findFirst();
    }

    public void addParam(String key, String value) {
        PolicyActionParam p = new PolicyActionParam();
        p.setAction(this);
        p.setParamKey(key);
        p.setParamValue(value);
        params.add(p);
    }
}
