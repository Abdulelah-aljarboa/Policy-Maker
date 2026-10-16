package com.example.demo.policySystem.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rule_value")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "rule")
public class RuleValue {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_value_seq")
    @SequenceGenerator(name = "rule_value_seq", sequenceName = "rule_value_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @Column(nullable = false, length = 500)
    private String value; // raw string; coerced to the real type at tree-build time
}
