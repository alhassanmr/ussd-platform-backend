package com.ussdplatform.repository;
import com.ussdplatform.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByName(String name);
    List<Plan> findByIsActiveTrue();
}
