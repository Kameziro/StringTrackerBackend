package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "club_photos")
@Getter
@Setter
@NoArgsConstructor
public class ClubPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false, length = 512)
    private String url;

    @Column(nullable = false)
    private int position;

    public static ClubPhoto create(Club club, String url, int position) {
        ClubPhoto photo = new ClubPhoto();
        photo.club = club;
        photo.url = url;
        photo.position = position;
        return photo;
    }
}
