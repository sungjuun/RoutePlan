package com.routeplan.contentimport.application;

import com.routeplan.contentimport.application.ContentImporter.ImportedContent;
import com.routeplan.contentimport.domain.ContentImport;
import com.routeplan.contentimport.domain.ContentImportCandidate;
import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.contentimport.persistence.ContentImportCandidateRepository;
import com.routeplan.contentimport.persistence.ContentImportRepository;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.place.search.PlaceSearchProvider;
import com.routeplan.place.search.PlaceSearchQuery;
import com.routeplan.place.search.PlaceSearchResult;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ContentImportProcessor {
    private static final Logger log = LoggerFactory.getLogger(ContentImportProcessor.class);

    private final ContentImportRepository importRepository;
    private final ContentImportCandidateRepository candidateRepository;
    private final ContentImporterRegistry importerRegistry;
    private final ContentPlaceExtractor extractor;
    private final PlaceSearchProvider placeSearchProvider;
    private final TransactionTemplate transactions;

    public ContentImportProcessor(
            ContentImportRepository importRepository,
            ContentImportCandidateRepository candidateRepository,
            ContentImporterRegistry importerRegistry,
            ContentPlaceExtractor extractor,
            PlaceSearchProvider placeSearchProvider,
            PlatformTransactionManager transactionManager
    ) {
        this.importRepository = importRepository;
        this.candidateRepository = candidateRepository;
        this.importerRegistry = importerRegistry;
        this.extractor = extractor;
        this.placeSearchProvider = placeSearchProvider;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void process(Long importId) {
        try {
            Work work = transactions.execute(status -> {
                ContentImport job = required(importId);
                job.processing();
                candidateRepository.deleteAllByContentImportId(importId);
                return new Work(
                        job.getId(), job.getOwner().getId(), job.getSourceType(),
                        URI.create(job.getSourceUrl()), job.getInputText()
                );
            });
            if (work == null) return;

            ImportedContent content = importerRegistry.get(work.sourceType()).load(work.sourceUrl(), work.inputText());
            if (content.requiresUserInput()) {
                transactions.executeWithoutResult(status -> required(importId).awaitInput(content.warning()));
                return;
            }

            List<String> placeNames = extractor.extract(work.userId(), content.title(), content.text());
            if (placeNames.isEmpty()) {
                transactions.executeWithoutResult(status -> {
                    ContentImport job = required(importId);
                    job.matching(content.title());
                    job.complete("명확한 장소명을 찾지 못했습니다. 장소명을 한 줄에 하나씩 입력해 다시 시도해 주세요.");
                });
                return;
            }

            transactions.executeWithoutResult(status -> required(importId).matching(content.title()));
            MatchBatch matched = match(placeNames);
            transactions.executeWithoutResult(status -> {
                ContentImport job = required(importId);
                candidateRepository.deleteAllByContentImportId(importId);
                List<ContentImportCandidate> entities = new ArrayList<>();
                for (CandidateData candidate : matched.candidates()) {
                    entities.add(candidate.result() == null
                            ? ContentImportCandidate.unmatched(job, candidate.mentionOrder(), candidate.extractedName())
                            : ContentImportCandidate.matched(job, candidate.mentionOrder(), candidate.matchRank(), candidate.extractedName(), candidate.result()));
                }
                candidateRepository.saveAll(entities);
                job.complete(matched.warning());
            });
        } catch (RuntimeException exception) {
            log.warn("콘텐츠 가져오기 작업 {} 처리 실패: {}", importId, exception.getMessage());
            try {
                transactions.executeWithoutResult(status -> required(importId).fail(safeMessage(exception)));
            } catch (RuntimeException persistenceFailure) {
                log.error("콘텐츠 가져오기 작업 {} 실패 상태 저장 불가", importId, persistenceFailure);
            }
        }
    }

    private MatchBatch match(List<String> placeNames) {
        List<CandidateData> candidates = new ArrayList<>();
        String warning = null;
        int order = 0;
        for (String name : placeNames) {
            order++;
            try {
                List<PlaceSearchResult> results = placeSearchProvider.search(new PlaceSearchQuery(name, null, 0, 3, "ko"));
                if (results.isEmpty()) {
                    candidates.add(new CandidateData(order, 0, name, null));
                    continue;
                }
                for (int rank = 0; rank < results.size(); rank++) {
                    candidates.add(new CandidateData(order, rank + 1, name, results.get(rank)));
                }
            } catch (ExternalProviderException exception) {
                candidates.add(new CandidateData(order, 0, name, null));
                warning = "장소 검색 Provider를 사용할 수 없어 추출한 이름만 표시합니다. Google Place Provider 설정 후 다시 시도할 수 있습니다.";
            }
        }
        return new MatchBatch(List.copyOf(candidates), warning);
    }

    private ContentImport required(Long id) {
        return importRepository.findById(id).orElseThrow(() -> new IllegalStateException("가져오기 작업이 삭제됐습니다."));
    }

    private String safeMessage(RuntimeException exception) {
        if (!(exception instanceof RoutePlanException) && !(exception instanceof ExternalProviderException)) {
            return "가져오기 작업을 처리하지 못했습니다. 입력을 확인한 뒤 다시 시도해 주세요.";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "가져오기 작업을 처리하지 못했습니다.";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record Work(Long id, Long userId, ContentSourceType sourceType, URI sourceUrl, String inputText) {}
    private record CandidateData(int mentionOrder, int matchRank, String extractedName, PlaceSearchResult result) {}
    private record MatchBatch(List<CandidateData> candidates, String warning) {}
}
