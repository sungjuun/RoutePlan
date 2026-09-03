package com.routeplan.wishlist.persistence;

import com.routeplan.wishlist.domain.WishlistPlace;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistPlaceRepository extends JpaRepository<WishlistPlace, Long> {
    @EntityGraph(attributePaths = "place")
    List<WishlistPlace> findAllByWishlistIdOrderByCreatedAtAsc(Long wishlistId);

    @EntityGraph(attributePaths = "place")
    List<WishlistPlace> findAllByWishlistIdAndIdInOrderByCreatedAtAsc(Long wishlistId, Collection<Long> ids);

    @EntityGraph(attributePaths = "place")
    Optional<WishlistPlace> findByIdAndWishlistId(Long id, Long wishlistId);

    boolean existsByWishlistIdAndPlaceId(Long wishlistId, Long placeId);
    long countByWishlistId(Long wishlistId);
}
