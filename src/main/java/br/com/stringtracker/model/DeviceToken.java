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
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
public class DeviceToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expo_push_token", nullable = false)
    private String expoPushToken;

    @Column(nullable = false, length = 32)
    private String platform;

    public static DeviceToken create(User user, String expoPushToken, String platform) {
        DeviceToken token = new DeviceToken();
        token.user = user;
        token.expoPushToken = expoPushToken;
        token.platform = platform;
        return token;
    }
}
