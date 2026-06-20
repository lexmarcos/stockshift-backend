package br.com.stockshift.repository;

import br.com.stockshift.dto.internal.bot.BotProductSearchProjection;
import br.com.stockshift.model.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BotProductSearchRepository extends JpaRepository<Batch, UUID> {

    @Query(value = """
            SELECT p.id AS "productId",
                   p.name AS "name",
                   p.image_url AS "imageUrl",
                   p.barcode AS "barcode",
                   p.sku AS "sku",
                   w.id AS "warehouseId",
                   w.name AS "warehouseName",
                   COALESCE(SUM(b.quantity), 0) AS "totalQuantity",
                   latest.selling_price AS "latestBatchSellingPrice",
                   latest.batch_code AS "latestBatchCode",
                   latest.created_at AS "latestBatchCreatedAt"
            FROM products p
            JOIN batches b ON b.product_id = p.id
                          AND b.warehouse_id = :warehouseId
                          AND b.tenant_id = :tenantId
                          AND b.deleted_at IS NULL
            JOIN warehouses w ON w.id = b.warehouse_id
                             AND w.tenant_id = :tenantId
                             AND w.is_active = true
            LEFT JOIN LATERAL (
                SELECT lb.selling_price, lb.batch_code, lb.created_at
                FROM batches lb
                WHERE lb.product_id = p.id
                  AND lb.warehouse_id = :warehouseId
                  AND lb.tenant_id = :tenantId
                  AND lb.deleted_at IS NULL
                ORDER BY lb.created_at DESC, lb.id DESC
                LIMIT 1
            ) latest ON true
            WHERE p.tenant_id = :tenantId
              AND p.deleted_at IS NULL
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:query AS text), '%'))
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', CAST(:query AS text), '%'))
                   OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', CAST(:query AS text), '%')))
            GROUP BY p.id, p.name, p.image_url, p.barcode, p.sku,
                     w.id, w.name,
                     latest.selling_price, latest.batch_code, latest.created_at
            ORDER BY p.name ASC, p.id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<BotProductSearchProjection> searchProductsForBot(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("query") String query,
            @Param("limitPlusOne") int limitPlusOne);
}
