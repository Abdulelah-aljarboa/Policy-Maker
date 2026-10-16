package com.example.demo.policySystem.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "policy_action_param")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "action")
public class PolicyActionParam {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "policy_action_param_seq")
    @SequenceGenerator(name = "policy_action_param_seq", sequenceName = "policy_action_param_id_seq", allocationSize = 20)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "action_id", nullable = false)
    private PolicyAction action;

    @Column(name = "param_key", nullable = false, length = 100)
    private String paramKey;

    @Column(name = "param_value", nullable = false, length = 500)
    private String paramValue;
}
