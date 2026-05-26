package com.ussdplatform.repository;
import com.ussdplatform.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface MenuRepository extends JpaRepository<Menu, UUID> {
    List<Menu> findByAppId(UUID appId);
    @Query("SELECT m FROM Menu m WHERE m.app.id = :appId AND m.isRoot = true")
    Optional<Menu> findRootMenuByAppId(UUID appId);
    @Modifying @Transactional
    @Query("UPDATE Menu m SET m.isRoot = false WHERE m.app.id = :appId")
    void clearRootForApp(UUID appId);
}
