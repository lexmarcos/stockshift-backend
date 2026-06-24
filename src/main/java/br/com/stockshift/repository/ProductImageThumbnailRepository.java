package br.com.stockshift.repository;

import br.com.stockshift.model.entity.ProductImageThumbnail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageThumbnailRepository
        extends JpaRepository<ProductImageThumbnail, ProductImageThumbnail.ProductImageThumbnailId> {

    List<ProductImageThumbnail> findByProductId(UUID productId);

    void deleteByProductId(UUID productId);
}
