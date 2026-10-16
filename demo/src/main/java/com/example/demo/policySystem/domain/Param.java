package com.example.demo.policySystem.domain;

import com.example.demo.policySystem.enums.ParamType;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "param", uniqueConstraints = @UniqueConstraint(columnNames = {"entity_name", "param_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "allowedOperators")
public class Param {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "param_seq")
    @SequenceGenerator(name = "param_seq", sequenceName = "param_id_seq", allocationSize = 20)
    private Long id;

    @Column(name = "entity_name", nullable = false, length = 50)
    private String entityName; // ESTABLISHMENT | PERSON | ENGAGEMENT

    @Column(name = "param_name", nullable = false, length = 100)
    private String paramName;

    @Enumerated(EnumType.STRING)
    @Column(name = "param_type", nullable = false, length = 20)
    private ParamType paramType;

    @OneToMany(mappedBy = "param", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ParamOperator> allowedOperators = new ArrayList<>();
}
