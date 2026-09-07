package com.routeplan.community.persistence;

import com.routeplan.community.domain.RouteSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteSaveRepository extends JpaRepository<RouteSave, Long> {
    boolean existsBySharedRouteIdAndUserId(Long sharedRouteId, Long userId);
    long deleteBySharedRouteIdAndUserId(Long sharedRouteId, Long userId);

    @EntityGraph(attributePaths = {"sharedRoute", "sharedRoute.owner"})
    @Query(value = """
            select saved from RouteSave saved
            where saved.user.id = :userId and saved.sharedRoute.moderatedHidden = false
            order by saved.createdAt desc, saved.id desc
            """, countQuery = """
            select count(saved) from RouteSave saved
            where saved.user.id = :userId and saved.sharedRoute.moderatedHidden = false
            """)
    Page<RouteSave> findVisibleByUserId(@Param("userId") Long userId, Pageable pageable);
}
