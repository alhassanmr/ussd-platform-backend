package com.ussdplatform.repository;
import com.ussdplatform.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
    List<MenuItem> findByMenuIdOrderByDisplayOrderAsc(UUID menuId);
}
