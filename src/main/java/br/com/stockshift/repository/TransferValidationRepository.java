package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferValidation;
import br.com.stockshift.model.enums.ValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferValidationRepository extends JpaRepository<TransferValidation, UUID> {

    Optional<TransferValidation> findByStockMovementIdAndStatus(UUID stockMovementId, ValidationStatus status);

    List<TransferValidation> findByStockMovementId(UUID stockMovementId);

    boolean existsByStockMovementIdAndStatusIn(UUID stockMovementId, List<ValidationStatus> statuses);
}
