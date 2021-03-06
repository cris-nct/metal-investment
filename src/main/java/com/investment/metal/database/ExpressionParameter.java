package com.investment.metal.database;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "expressionfunctionparameters")
@Data
public class ExpressionParameter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long expressionFunctionId;

    private String name;

    private double min;

    private double max;

}
