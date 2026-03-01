package br.com.stockshift.repository;

import br.com.stockshift.model.entity.StockMovementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementItemRepository extends JpaRepository<StockMovementItem, UUID> {
  List<StockMovementItem> findByStockMovementId(UUID stockMovementId);
}
