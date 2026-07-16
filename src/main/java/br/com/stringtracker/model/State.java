package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "state", uniqueConstraints = {
        @UniqueConstraint(name = "uk_state_uf", columnNames = "uf"),
        @UniqueConstraint(name = "uk_state_ibge_code", columnNames = "ibge_code")
})
@SQLRestriction("active = true")
@Getter
@Setter
@NoArgsConstructor
public class State {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "ibge_code")
    private Long ibgeCode;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(nullable = false, length = 100)
    private String name;
}
