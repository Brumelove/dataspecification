package com.brume.dynamicdatamapper.domian.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

/**
 * @author Brume
 **/
@Data
@NoArgsConstructor
@Entity
public class DataSpecification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    private Integer providerId;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Fields> fields ;
}
