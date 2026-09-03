package com.routeplan.contentimport.persistence;

import com.routeplan.contentimport.domain.ContentImportCandidate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentImportCandidateRepository extends JpaRepository<ContentImportCandidate, Long> {
    List<ContentImportCandidate> findAllByContentImportIdOrderByMentionOrderAscMatchRankAsc(Long importId);
    List<ContentImportCandidate> findAllByContentImportIdAndIdIn(Long importId, Collection<Long> ids);
    void deleteAllByContentImportId(Long importId);
}
