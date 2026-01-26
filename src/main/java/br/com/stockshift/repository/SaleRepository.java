package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Sale;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {
    
    @Query("SELECT s FROM Sale s WHERE s.tenant.id = :tenantId AND s.id = :id")
    Optional<Sale> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);
    
    @Query("SELECT s FROM Sale s WHERE s.tenant.id = :tenantId")
    Page<Sale> findAllByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);
    
    @Query("SELECT s FROM Sale s WHERE s.tenant.id = :tenantId AND s.status = :status")
    Page<Sale> findByTenantIdAndStatus(@Param("tenantId") Long tenantId, 
                                       @Param("status") SaleStatus status, 
                                       Pageable pageable);
    
    @Query("SELECT s FROM Sale s WHERE s.tenant.id = :tenantId AND s.paymentMethod = :paymentMethod")
    Page<Sale> findByTenantIdAndPaymentMethod(@Param("tenantId") Long tenantId, 
                                               @Param("paymentMethod") PaymentMethod paymentMethod, 
                                               Pageable pageable);
    
    @Query("SELECT s FROM Sale s WHERE s.tenant.id = :tenantId " +
           "AND s.createdAt BETWEEN :startDate AND :endDate")
    Page<Sale> findByTenantIdAndDateRange(@Param("tenantId") Long tenantId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate,
                                          Pageable pageable);
}
