package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    // Set when this token is replaced by a newer one. A non-null value means the
    // token is being phased out but stays usable until its grace window closes,
    // so concurrent refreshes sharing the same cookie don't kill the session.
    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    // The single token that replaced this one on rotation. Set together with
    // rotatedAt. Lets a rotated-but-in-grace token return its tracked successor
    // instead of minting a new long-lived token (see RefreshTokenService).
    @Column(name = "replaced_by_id")
    private UUID replacedById;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }

    /**
     * Whether this token was rotated and its grace window has already elapsed.
     * Takes a {@link Duration} so sub-second windows keep their precision. Example:
     * {@code isRotationGraceExpired(Duration.ofSeconds(60))} rejects a token rotated
     * more than 60 seconds ago, while accepting one rotated 5 seconds ago.
     */
    public boolean isRotationGraceExpired(Duration grace) {
        return rotatedAt != null && LocalDateTime.now().isAfter(rotatedAt.plus(grace));
    }
}
