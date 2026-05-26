package com.ussdplatform.repository;
import com.ussdplatform.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<Invoice> findByPaystackRef(String ref);
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByStatus(Invoice.InvoiceStatus status);
    List<Invoice> findByStatusAndCreatedAtAfter(Invoice.InvoiceStatus status, LocalDateTime after);
}
