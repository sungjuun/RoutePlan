package com.routeplan.wishlist.persistence;

import com.routeplan.wishlist.domain.Wishlist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    List<Wishlist> findAllByOwnerIdOrderByUpdatedAtDesc(Long userId);
    Optional<Wishlist> findByIdAndOwnerId(Long id, Long userId);
    boolean existsByOwnerIdAndNameIgnoreCase(Long userId, String name);
}
