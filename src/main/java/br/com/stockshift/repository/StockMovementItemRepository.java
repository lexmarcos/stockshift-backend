package br.com.stockshift.repository;

import br.com.stockshift.model.entity.StockMovementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementItemRepository extends JpaRepository<StockMovementItem, UUID> {

    @Query("SELECT i FROM StockMovementItem i WHERE i.movement.id = :movementId")
    List<StockMovementItem> findByMovementId(UUID movementId);

    @Query("SELECT i FROM StockMovementItem i WHERE i.product.id = :productId")
    List<StockMovementItem> findByProductId(UUID productId);

    @Query("SELECT i FROM StockMovementItem i WHERE i.batch.id = :batchId")
    List<StockMovementItem> findByBatchId(UUID batchId);
}
