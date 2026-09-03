package com.routeplan.contentimport.persistence;

import com.routeplan.contentimport.domain.ContentImport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentImportRepository extends JpaRepository<ContentImport, Long> {
    Optional<ContentImport> findByIdAndOwnerId(Long id, Long userId);
}
