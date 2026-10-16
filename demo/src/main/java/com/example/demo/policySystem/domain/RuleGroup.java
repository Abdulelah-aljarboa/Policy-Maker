package com.example.demo.policySystem.domain;


import com.example.demo.policySystem.enums.LogicOperator;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rule_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"parent", "childGroups", "rules"}) // avoid recursive toString stack overflow
public class RuleGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_group_seq")
    @SequenceGenerator(name = "rule_group_seq", sequenceName = "rule_group_id_seq", allocationSize = 50)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "logic_operator", nullable = false, length = 3)
    private LogicOperator logicOperator;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @ManyToOne
    @JoinColumn(name = "parent_group_id")
    private RuleGroup parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RuleGroup> childGroups = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Rule> rules = new ArrayList<>();

    public void addChildGroup(RuleGroup child) {
        child.setParent(this);
        childGroups.add(child);
    }

    public void addRule(Rule rule) {
        rule.setGroup(this);
        rules.add(rule);
    }
}
