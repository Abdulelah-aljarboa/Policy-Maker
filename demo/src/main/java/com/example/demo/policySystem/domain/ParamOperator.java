package com.example.demo.policySystem.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "param_operator", uniqueConstraints = @UniqueConstraint(columnNames = {"param_id", "operator"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "param")
public class ParamOperator {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "param_operator_seq")
    @SequenceGenerator(name = "param_operator_seq", sequenceName = "param_operator_id_seq", allocationSize = 20)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "param_id", nullable = false)
    private Param param;

    @Column(nullable = false, length = 20)
    private String operator; // '=', '!=', '>', '<', 'IN', 'NOT_IN', 'BETWEEN', ...
}
