package com.example.demo.policySystem.domain;

import com.example.demo.policySystem.enums.PolicyStatus;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"rootGroup", "actions"}) // avoid huge recursive toString
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "policy_seq")
    @SequenceGenerator(name = "policy_seq", sequenceName = "policy_id_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "system_code", nullable = false, length = 50)
    private String systemCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PolicyStatus status = PolicyStatus.DRAFT;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "root_group_id", nullable = false)
    private RuleGroup rootGroup;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PolicyAction> actions = new ArrayList<>();

    public void addAction(PolicyAction action) {
        action.setPolicy(this);
        actions.add(action);
    }
}
