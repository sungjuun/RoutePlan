package com.routeplan.community.persistence;

import com.routeplan.community.domain.RouteLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteLikeRepository extends JpaRepository<RouteLike, Long> {

    boolean existsBySharedRouteIdAndUserId(Long sharedRouteId, Long userId);

    long deleteBySharedRouteIdAndUserId(Long sharedRouteId, Long userId);
}
