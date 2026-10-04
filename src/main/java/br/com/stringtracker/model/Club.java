package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clubs")
@Getter
@Setter
@NoArgsConstructor
public class Club extends BaseEntity {

    @Column(nullable = false)
    private String name;

    public static Club create(String name) {
        Club club = new Club();
        club.name = name;
        return club;
    }
}
