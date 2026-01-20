package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferDiscrepancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferDiscrepancyRepository extends JpaRepository<TransferDiscrepancy, UUID> {

    List<TransferDiscrepancy> findByTransferValidationId(UUID transferValidationId);
}
