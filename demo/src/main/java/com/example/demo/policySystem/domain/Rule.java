package com.example.demo.policySystem.domain;

import com.example.demo.policySystem.enums.ValueType;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"group", "values"})
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_seq")
    @SequenceGenerator(name = "rule_seq", sequenceName = "rule_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private RuleGroup group;

    @ManyToOne
    @JoinColumn(name = "param_id", nullable = false)
    private Param param;

    @Column(nullable = false, length = 20)
    private String operator;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 10)
    @Builder.Default
    private ValueType valueType = ValueType.LITERAL;

    @ManyToOne
    @JoinColumn(name = "ref_param_id")
    private Param refParam; // set only when valueType = PARAM_REF

    @Column(nullable = false)
    @Builder.Default
    private Boolean negate = false;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RuleValue> values = new ArrayList<>();

    public void addValue(String rawValue) {
        RuleValue rv = new RuleValue();
        rv.setRule(this);
        rv.setValue(rawValue);
        values.add(rv);
    }
}
