package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_validation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferValidationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transfer_item_id")
    private UUID transferItemId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(nullable = false)
    private String barcode;

    @Column(name = "validated_by_user_id", nullable = false)
    private UUID validatedByUserId;

    @Column(name = "validated_at", nullable = false)
    @Builder.Default
    private Instant validatedAt = Instant.now();

    @Column(nullable = false)
    private Boolean valid;
}
