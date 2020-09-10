package com.investment.metal.database;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "banip")
@Data
public class BanIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String ip;

}
