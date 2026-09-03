package com.routeplan.contentimport.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.contentimport.application.ContentSourceDetector.DetectedSource;
import com.routeplan.contentimport.domain.ContentImport;
import com.routeplan.contentimport.domain.ContentImportCandidate;
import com.routeplan.contentimport.domain.ContentImportStatus;
import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.contentimport.persistence.ContentImportCandidateRepository;
import com.routeplan.contentimport.persistence.ContentImportRepository;
import com.routeplan.place.application.PlaceService;
import com.routeplan.place.application.PlaceService.ImportPlaceResult;
import com.routeplan.user.domain.User;
import com.routeplan.user.persistence.UserRepository;
import com.routeplan.wishlist.application.WishlistService;
import com.routeplan.wishlist.application.WishlistService.WishlistResult;
import com.routeplan.wishlist.domain.Wishlist;
import com.routeplan.wishlist.domain.WishlistPriority;
import com.routeplan.wishlist.persistence.WishlistRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ContentImportService {
    private final UserRepository userRepository;
    private final WishlistRepository wishlistRepository;
    private final ContentImportRepository importRepository;
    private final ContentImportCandidateRepository candidateRepository;
    private final ContentSourceDetector sourceDetector;
    private final ContentImportProcessor processor;
    private final TaskExecutor executor;
    private final TransactionTemplate transactions;
    private final PlaceService placeService;
    private final WishlistService wishlistService;

    public ContentImportService(
            UserRepository userRepository,
            WishlistRepository wishlistRepository,
            ContentImportRepository importRepository,
            ContentImportCandidateRepository candidateRepository,
            ContentSourceDetector sourceDetector,
            ContentImportProcessor processor,
            @Qualifier("contentImportExecutor") TaskExecutor executor,
            PlatformTransactionManager transactionManager,
            PlaceService placeService,
            WishlistService wishlistService
    ) {
        this.userRepository = userRepository;
        this.wishlistRepository = wishlistRepository;
        this.importRepository = importRepository;
        this.candidateRepository = candidateRepository;
        this.sourceDetector = sourceDetector;
        this.processor = processor;
        this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
        this.placeService = placeService;
        this.wishlistService = wishlistService;
    }

    public ContentImportResult start(Long userId, String url, String inputText, Long wishlistId) {
        DetectedSource detected = sourceDetector.detect(url);
        Long importId = transactions.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RoutePlanException(ErrorCode.USER_NOT_FOUND));
            Wishlist wishlist = wishlistId == null ? null : wishlistRepository.findByIdAndOwnerId(wishlistId, userId)
                    .orElseThrow(() -> new RoutePlanException(ErrorCode.WISHLIST_NOT_FOUND));
            return importRepository.save(ContentImport.create(
                    user, wishlist, detected.sourceType(), detected.uri().toString(), inputText
            )).getId();
        });
        dispatch(importId);
        return get(userId, importId);
    }

    public ContentImportResult retry(Long userId, Long importId, String inputText) {
        transactions.executeWithoutResult(status -> {
            ContentImport job = owned(userId, importId);
            if (!(job.getStatus() == ContentImportStatus.AWAITING_INPUT || job.getStatus() == ContentImportStatus.FAILED
                    || job.getStatus() == ContentImportStatus.COMPLETED)) {
                throw new RoutePlanException(ErrorCode.CONTENT_IMPORT_NOT_READY, "처리 중인 작업은 다시 시작할 수 없습니다.");
            }
            candidateRepository.deleteAllByContentImportId(importId);
            job.retry(inputText);
        });
        dispatch(importId);
        return get(userId, importId);
    }

    public ContentImportResult get(Long userId, Long importId) {
        return transactions.execute(status -> {
            ContentImport job = owned(userId, importId);
            List<ContentImportCandidate> candidates = candidateRepository
                    .findAllByContentImportIdOrderByMentionOrderAscMatchRankAsc(importId);
            return result(job, candidates);
        });
    }

    public WishlistResult saveCandidates(Long userId, Long importId, Long wishlistId, List<Long> candidateIds) {
        Selection selection = transactions.execute(status -> {
            ContentImport job = owned(userId, importId);
            if (job.getStatus() != ContentImportStatus.COMPLETED) {
                throw new RoutePlanException(ErrorCode.CONTENT_IMPORT_NOT_READY);
            }
            wishlistRepository.findByIdAndOwnerId(wishlistId, userId)
                    .orElseThrow(() -> new RoutePlanException(ErrorCode.WISHLIST_NOT_FOUND));
            Set<Long> uniqueIds = new HashSet<>(candidateIds);
            if (uniqueIds.size() != candidateIds.size()) throw new IllegalArgumentException("같은 후보를 중복 선택할 수 없습니다.");
            List<ContentImportCandidate> candidates = candidateRepository.findAllByContentImportIdAndIdIn(importId, uniqueIds);
            if (candidates.size() != uniqueIds.size() || candidates.stream().anyMatch(candidate -> !candidate.matched())) {
                throw new RoutePlanException(ErrorCode.CONTENT_IMPORT_CANDIDATE_NOT_FOUND);
            }
            long mentionCount = candidates.stream().map(ContentImportCandidate::getMentionOrder).distinct().count();
            if (mentionCount != candidates.size()) throw new IllegalArgumentException("장소 언급 하나당 후보 한 곳만 선택해 주세요.");
            return new Selection(job.getSourceType(), job.getSourceUrl(), List.copyOf(candidates));
        });

        for (ContentImportCandidate candidate : selection.candidates()) {
            ImportPlaceResult imported = placeService.importExternal(
                    candidate.getExternalPlaceId(), candidate.getMatchedName(), candidate.getLatitude(),
                    candidate.getLongitude(), candidate.getPrimaryType(), 60, null
            );
            try {
                wishlistService.addPlace(
                        userId, wishlistId, imported.place().id(), WishlistPriority.NORMAL,
                        selection.sourceType(), selection.sourceUrl(), null, null
                );
            } catch (RoutePlanException exception) {
                if (exception.errorCode() != ErrorCode.DUPLICATE_WISHLIST_PLACE) throw exception;
            } catch (DataIntegrityViolationException exception) {
                // Concurrent identical saves are idempotent from the user's perspective.
            }
        }
        return wishlistService.get(userId, wishlistId);
    }

    private void dispatch(Long importId) {
        try {
            executor.execute(() -> processor.process(importId));
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> importRepository.findById(importId)
                    .ifPresent(job -> job.fail("가져오기 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.")));
        }
    }

    private ContentImport owned(Long userId, Long importId) {
        return importRepository.findByIdAndOwnerId(importId, userId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.CONTENT_IMPORT_NOT_FOUND));
    }

    private ContentImportResult result(ContentImport job, List<ContentImportCandidate> candidates) {
        return new ContentImportResult(
                job.getId(), job.getWishlist() == null ? null : job.getWishlist().getId(), job.getSourceType(),
                job.getStatus(), job.getSourceUrl(), job.getDetectedTitle(), job.getWarning(), job.getErrorMessage(),
                job.getCreatedAt(), job.getUpdatedAt(), job.getStartedAt(), job.getCompletedAt(),
                candidates.stream().map(ContentImportCandidateResult::from).toList()
        );
    }

    private record Selection(ContentSourceType sourceType, String sourceUrl, List<ContentImportCandidate> candidates) {}

    public record ContentImportResult(
            Long id, Long wishlistId, ContentSourceType sourceType, ContentImportStatus status,
            String sourceUrl, String detectedTitle, String warning, String errorMessage,
            Instant createdAt, Instant updatedAt, Instant startedAt, Instant completedAt,
            List<ContentImportCandidateResult> candidates
    ) {}

    public record ContentImportCandidateResult(
            Long id, int mentionOrder, int matchRank, String extractedName, boolean matched,
            String externalPlaceId, String matchedName, String formattedAddress,
            BigDecimal latitude, BigDecimal longitude, String primaryType, String provider
    ) {
        static ContentImportCandidateResult from(ContentImportCandidate candidate) {
            return new ContentImportCandidateResult(
                    candidate.getId(), candidate.getMentionOrder(), candidate.getMatchRank(),
                    candidate.getExtractedName(), candidate.matched(), candidate.getExternalPlaceId(),
                    candidate.getMatchedName(), candidate.getFormattedAddress(), candidate.getLatitude(),
                    candidate.getLongitude(), candidate.getPrimaryType(), candidate.getProvider()
            );
        }
    }
}
