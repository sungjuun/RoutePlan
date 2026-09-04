package com.routeplan.trip.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.collaboration.domain.TripMemberRole;
import com.routeplan.place.domain.Place;
import com.routeplan.place.domain.PlaceEnvironment;
import com.routeplan.place.persistence.PlaceRepository;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPlace;
import com.routeplan.trip.domain.TripPace;
import com.routeplan.trip.domain.TripStatus;
import com.routeplan.trip.persistence.TripPlaceRepository;
import com.routeplan.trip.persistence.TripRepository;
import com.routeplan.user.domain.User;
import com.routeplan.user.persistence.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    static final int MAX_PLACES_PER_TRIP = 50;

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final PlaceRepository placeRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public TripService(
            UserRepository userRepository,
            TripRepository tripRepository,
            PlaceRepository placeRepository,
            TripPlaceRepository tripPlaceRepository,
            org.springframework.jdbc.core.JdbcTemplate jdbc
    ) {
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.placeRepository = placeRepository;
        this.tripPlaceRepository = tripPlaceRepository;
        this.jdbc = jdbc;
    }

    @Transactional
    public TripResult create(CreateTripCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.USER_NOT_FOUND));
        Trip trip = Trip.create(
                user,
                command.name(),
                command.startDate(),
                command.endDate(),
                command.accommodationName(),
                command.accommodationLatitude(),
                command.accommodationLongitude(),
                command.transportMode(),
                command.dailyStartTime() == null ? LocalTime.of(9, 0) : command.dailyStartTime(),
                command.dailyEndTime() == null ? LocalTime.of(20, 0) : command.dailyEndTime(),
                command.pace() == null ? TripPace.STANDARD : command.pace()
        );
        Trip saved = tripRepository.saveAndFlush(trip);
        addOwnerMembership(saved.getId(), user.getId());
        return toResult(saved, List.of());
    }

    @Transactional
    public TripResult createFromSnapshot(
            CreateTripCommand command,
            List<AddTripPlaceCommand> placeCommands
    ) {
        if (placeCommands == null || placeCommands.isEmpty()) {
            throw new RoutePlanException(ErrorCode.TRIP_HAS_NO_PLACES);
        }
        if (placeCommands.size() > MAX_PLACES_PER_TRIP) {
            throw new RoutePlanException(ErrorCode.TRIP_PLACE_LIMIT_EXCEEDED);
        }
        Map<Long, AddTripPlaceCommand> uniqueCommands = placeCommands.stream()
                .collect(Collectors.toMap(
                        AddTripPlaceCommand::placeId,
                        Function.identity(),
                        (left, right) -> {
                            throw new RoutePlanException(ErrorCode.DUPLICATE_TRIP_PLACE);
                        },
                        LinkedHashMap::new
                ));
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.USER_NOT_FOUND));
        Map<Long, Place> placesById = placeRepository.findAllById(uniqueCommands.keySet()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        if (placesById.size() != uniqueCommands.size()) {
            throw new RoutePlanException(ErrorCode.PLACE_NOT_FOUND);
        }

        Trip trip = tripRepository.saveAndFlush(Trip.create(
                user,
                command.name(),
                command.startDate(),
                command.endDate(),
                command.accommodationName(),
                command.accommodationLatitude(),
                command.accommodationLongitude(),
                command.transportMode(),
                command.dailyStartTime() == null ? LocalTime.of(9, 0) : command.dailyStartTime(),
                command.dailyEndTime() == null ? LocalTime.of(20, 0) : command.dailyEndTime(),
                command.pace() == null ? TripPace.STANDARD : command.pace()
        ));
        addOwnerMembership(trip.getId(), user.getId());
        List<TripPlace> tripPlaces = uniqueCommands.values().stream()
                .map(placeCommand -> new TripPlace(
                        trip,
                        placesById.get(placeCommand.placeId()),
                        placeCommand.priority(),
                        placeCommand.mustVisit(),
                        placeCommand.preferredStartTime(),
                        placeCommand.preferredEndTime(),
                        placeCommand.minimumStayMinutes(),
                        placeCommand.maximumStayMinutes()
                ))
                .toList();
        return toResult(trip, tripPlaceRepository.saveAll(tripPlaces));
    }

    @Transactional(readOnly = true)
    public TripResult get(Long tripId) {
        Trip trip = getTrip(tripId);
        return toResult(trip, tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId));
    }

    @Transactional(readOnly = true)
    public List<TripSummaryResult> list(Long userId) {
        return jdbc.query("""
                SELECT trip.id, trip.user_id, trip.name, trip.start_date, trip.end_date,
                       trip.accommodation_name, trip.transport_mode, trip.pace, trip.status,
                       trip.created_at, trip.updated_at,
                       (SELECT count(*) FROM trip_places place WHERE place.trip_id=trip.id) AS place_count,
                       CASE WHEN trip.user_id=? THEN 'OWNER' ELSE member.role END AS access_role
                FROM trips trip
                LEFT JOIN trip_members member ON member.trip_id=trip.id AND member.user_id=?
                WHERE trip.user_id=? OR member.user_id IS NOT NULL
                ORDER BY trip.updated_at DESC
                """, (rs, row) -> new TripSummaryResult(
                rs.getLong("id"),
                rs.getLong("user_id"),
                TripMemberRole.valueOf(rs.getString("access_role")),
                rs.getString("name"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getString("accommodation_name"),
                TransportMode.valueOf(rs.getString("transport_mode")),
                TripPace.valueOf(rs.getString("pace")),
                TripStatus.valueOf(rs.getString("status")),
                rs.getLong("place_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        ), userId, userId, userId);
    }

    @Transactional
    public TripResult update(Long tripId, UpdateTripCommand command) {
        Trip trip = getTripForUpdate(tripId);
        LocalDate start = command.startDate() == null ? trip.getStartDate() : command.startDate();
        LocalDate end = command.endDate() == null ? trip.getEndDate() : command.endDate();
        Long outside = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM trip_expenses WHERE trip_id=? AND (spend_date<? OR spend_date>?))
                     + (SELECT count(*) FROM trip_budget_allocations WHERE trip_id=? AND (spend_date<? OR spend_date>?))
                """,Long.class,tripId,start,end,tripId,start,end);
        if (outside != null && outside > 0) throw new RoutePlanException(ErrorCode.CONFLICT,
                "새 여행 기간 밖에 지출 또는 날짜 예산이 있습니다. 기록의 날짜를 먼저 정리해 주세요.");
        BigDecimal latitude = command.accommodationLatitude() == null
                ? trip.getAccommodationLatitude() : command.accommodationLatitude();
        BigDecimal longitude = command.accommodationLongitude() == null
                ? trip.getAccommodationLongitude() : command.accommodationLongitude();

        trip.update(
                command.name() == null ? trip.getName() : command.name(),
                command.startDate() == null ? trip.getStartDate() : command.startDate(),
                command.endDate() == null ? trip.getEndDate() : command.endDate(),
                command.accommodationName() == null ? trip.getAccommodationName() : command.accommodationName(),
                latitude,
                longitude,
                command.transportMode() == null ? trip.getTransportMode() : command.transportMode(),
                command.dailyStartTime() == null ? trip.getDailyStartTime() : command.dailyStartTime(),
                command.dailyEndTime() == null ? trip.getDailyEndTime() : command.dailyEndTime(),
                command.pace() == null ? trip.getPace() : command.pace()
        );
        return toResult(trip, tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId));
    }

    @Transactional
    public void updateTimeZone(Long tripId, String zone) {
        getTripForUpdate(tripId).updateTimeZone(zone);
    }

    @Transactional
    public TripResult addPlace(Long tripId, Long placeId) {
        return addPlace(tripId, new AddTripPlaceCommand(
                placeId, 50, false, null, null, null, null
        ));
    }

    @Transactional
    public TripResult addPlace(Long tripId, AddTripPlaceCommand command) {
        Trip trip = getTripForUpdate(tripId);
        if (tripPlaceRepository.existsByTripIdAndPlaceId(tripId, command.placeId())) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_TRIP_PLACE);
        }
        if (tripPlaceRepository.countByTripId(tripId) >= MAX_PLACES_PER_TRIP) {
            throw new RoutePlanException(ErrorCode.TRIP_PLACE_LIMIT_EXCEEDED);
        }
        Place place = placeRepository.findById(command.placeId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.PLACE_NOT_FOUND));
        tripPlaceRepository.save(new TripPlace(
                trip,
                place,
                command.priority(),
                command.mustVisit(),
                command.preferredStartTime(),
                command.preferredEndTime(),
                command.minimumStayMinutes(),
                command.maximumStayMinutes()
        ));
        trip.markDraft();
        return toResult(trip, tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId));
    }

    @Transactional
    public TripResult updatePlaceConstraints(Long tripId, Long placeId, UpdateTripPlaceCommand command) {
        Trip trip = getTripForUpdate(tripId);
        TripPlace tripPlace = tripPlaceRepository.findByTripIdAndPlaceId(tripId, placeId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_PLACE_NOT_FOUND));
        tripPlace.updateConstraints(
                command.priority(),
                command.mustVisit(),
                command.preferredStartTime(),
                command.preferredEndTime(),
                command.minimumStayMinutes(),
                command.maximumStayMinutes()
        );
        trip.markDraft();
        return toResult(trip, tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId));
    }

    @Transactional
    public void removePlace(Long tripId, Long placeId) {
        Trip trip = getTripForUpdate(tripId);
        if (tripPlaceRepository.deleteByTripIdAndPlaceId(tripId, placeId) == 0) {
            throw new RoutePlanException(ErrorCode.TRIP_PLACE_NOT_FOUND);
        }
        trip.markDraft();
    }

    @Transactional
    public TripResult applyStructuredConstraints(
            Long tripId,
            ApplyStructuredConstraintsCommand command
    ) {
        Trip trip = getTripForUpdate(tripId);
        List<TripPlace> tripPlaces = tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId);
        Map<Long, TripPlace> placesById = tripPlaces.stream()
                .collect(Collectors.toMap(
                        tripPlace -> tripPlace.getPlace().getId(),
                        Function.identity()
                ));
        if (command.placeConstraints() == null) {
            throw new IllegalArgumentException("장소 제약 목록은 필수입니다.");
        }
        HashSet<Long> requestedIds = new HashSet<>();
        for (ApplyPlaceConstraintCommand placeCommand : command.placeConstraints()) {
            if (!requestedIds.add(placeCommand.placeId())) {
                throw new IllegalArgumentException("같은 장소의 제약을 중복 적용할 수 없습니다.");
            }
            TripPlace tripPlace = placesById.get(placeCommand.placeId());
            if (tripPlace == null) {
                throw new RoutePlanException(ErrorCode.TRIP_PLACE_NOT_FOUND);
            }
            tripPlace.updateConstraints(
                    placeCommand.priority(),
                    placeCommand.mustVisit(),
                    placeCommand.preferredStartTime(),
                    placeCommand.preferredEndTime(),
                    placeCommand.minimumStayMinutes(),
                    placeCommand.maximumStayMinutes()
            );
        }
        trip.update(
                trip.getName(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getAccommodationName(),
                trip.getAccommodationLatitude(),
                trip.getAccommodationLongitude(),
                command.transportMode(),
                command.dailyStartTime(),
                command.dailyEndTime(),
                command.pace()
        );
        return toResult(trip, tripPlaces);
    }

    private Trip getTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
    }

    private Trip getTripForUpdate(Long tripId) {
        return tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
    }

    private void addOwnerMembership(Long tripId, Long userId) {
        jdbc.update("""
                INSERT INTO trip_members(trip_id,user_id,role,invited_by)
                VALUES(?,?,'OWNER',?)
                ON CONFLICT(trip_id,user_id) DO NOTHING
                """, tripId, userId, userId);
    }

    private TripResult toResult(Trip trip, List<TripPlace> tripPlaces) {
        List<TripPlaceResult> places = tripPlaces.stream()
                .map(tripPlace -> {
                    Place place = tripPlace.getPlace();
                    return new TripPlaceResult(
                            place.getId(),
                            place.getName(),
                            place.getLatitude(),
                            place.getLongitude(),
                            place.getCategory(),
                            place.getAverageStayMinutes(),
                            place.getEnvironment(),
                            tripPlace.getPriority(),
                            tripPlace.isMustVisit(),
                            tripPlace.getPreferredStartTime(),
                            tripPlace.getPreferredEndTime(),
                            tripPlace.getMinimumStayMinutes(),
                            tripPlace.getMaximumStayMinutes(),
                            place.getExternalPlaceId()
                    );
                })
                .toList();

        return new TripResult(
                trip.getId(),
                trip.getUser().getId(),
                trip.getName(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getDailyStartTime(),
                trip.getDailyEndTime(),
                trip.getAccommodationName(),
                trip.getAccommodationLatitude(),
                trip.getAccommodationLongitude(),
                trip.getTransportMode(),
                trip.getPace(),
                trip.getStatus(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                places
        );
    }

    public record CreateTripCommand(
            Long userId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            TripPace pace
    ) {
    }

    public record UpdateTripCommand(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            TripPace pace
    ) {
    }

    public record AddTripPlaceCommand(
            Long placeId,
            int priority,
            boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            Integer minimumStayMinutes,
            Integer maximumStayMinutes
    ) {
    }

    public record UpdateTripPlaceCommand(
            int priority,
            boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            Integer minimumStayMinutes,
            Integer maximumStayMinutes
    ) {
    }

    public record ApplyStructuredConstraintsCommand(
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            TripPace pace,
            TransportMode transportMode,
            List<ApplyPlaceConstraintCommand> placeConstraints
    ) {
    }

    public record ApplyPlaceConstraintCommand(
            Long placeId,
            int priority,
            boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            Integer minimumStayMinutes,
            Integer maximumStayMinutes
    ) {
    }

    public record TripResult(
            Long id,
            Long userId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode,
            TripPace pace,
            TripStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<TripPlaceResult> places
    ) {
    }

    public record TripSummaryResult(
            Long id,
            Long ownerId,
            TripMemberRole accessRole,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            TransportMode transportMode,
            TripPace pace,
            TripStatus status,
            long placeCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record TripPlaceResult(
            Long placeId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            int averageStayMinutes,
            PlaceEnvironment environment,
            int priority,
            boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            Integer minimumStayMinutes,
            Integer maximumStayMinutes,
            String externalPlaceId
    ) {
    }
}
