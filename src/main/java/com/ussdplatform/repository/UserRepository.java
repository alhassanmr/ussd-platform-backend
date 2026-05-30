package com.ussdplatform.repository;

import com.ussdplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // JOIN FETCH tenant so it's always loaded — prevents LazyInitializationException
    @Query("SELECT u FROM User u JOIN FETCH u.tenant WHERE u.email = :email")
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u JOIN FETCH u.tenant WHERE u.phone = :phone")
    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    @Query("SELECT u FROM User u JOIN FETCH u.tenant WHERE u.tenant.id = :tenantId")
    List<User> findByTenantId(UUID tenantId);
}
