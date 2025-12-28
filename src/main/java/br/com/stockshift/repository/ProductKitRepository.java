package br.com.stockshift.repository;

import br.com.stockshift.model.entity.ProductKit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductKitRepository extends JpaRepository<ProductKit, UUID> {

    @Query("SELECT pk FROM ProductKit pk WHERE pk.kitProduct.id = :kitProductId")
    List<ProductKit> findByKitProductId(UUID kitProductId);

    @Query("SELECT pk FROM ProductKit pk WHERE pk.componentProduct.id = :componentProductId")
    List<ProductKit> findByComponentProductId(UUID componentProductId);
}
