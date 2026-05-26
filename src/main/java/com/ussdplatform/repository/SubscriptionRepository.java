package com.ussdplatform.repository;
import com.ussdplatform.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByTenantId(UUID tenantId);
    List<Subscription> findByStatus(Subscription.SubscriptionStatus status);
    Optional<Subscription> findByPaystackSubCode(String subCode);
}
