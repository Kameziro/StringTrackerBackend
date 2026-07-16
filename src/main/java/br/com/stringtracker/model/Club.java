package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clubs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_clubs_name", columnNames = "name")
})
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
