package com.routeplan.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 리소스에 접근할 권한이 없습니다."),
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    DUPLICATE_TRIP_PLACE(HttpStatus.CONFLICT, "여행에 이미 추가된 장소입니다."),
    TRIP_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "여행에 등록된 장소가 아닙니다."),
    TRIP_PLACE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "한 여행에는 장소를 최대 50개까지 추가할 수 있습니다."),
    TRIP_HAS_NO_PLACES(HttpStatus.UNPROCESSABLE_CONTENT, "최적화할 장소가 없습니다."),
    EXACT_SEARCH_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "Exact Search는 장소를 최대 10개까지 지원합니다."),
    INFEASIBLE_MUST_VISIT(HttpStatus.UNPROCESSABLE_CONTENT, "현재 조건으로는 모든 MUST_VISIT 장소를 방문할 수 없습니다."),
    INFEASIBLE_RETURN(HttpStatus.UNPROCESSABLE_CONTENT, "현재 위치에서 하루 종료 전 숙소로 돌아갈 수 없습니다."),
    EXTERNAL_PROVIDER_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "외부 데이터 Provider가 설정되지 않았습니다."),
    EXTERNAL_PROVIDER_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "외부 데이터 Provider의 요청 한도를 초과했습니다."),
    EXTERNAL_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "외부 데이터 Provider를 사용할 수 없습니다."),
    EXTERNAL_PROVIDER_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "외부 데이터 Provider 응답이 올바르지 않습니다."),
    ROUTE_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "이동 가능한 경로를 찾을 수 없습니다."),
    OPTIMIZATION_INPUT_CHANGED(HttpStatus.CONFLICT, "최적화 중 여행 조건이 변경됐습니다. 다시 시도해 주세요."),
    ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    ITINERARY_NOT_SHAREABLE(HttpStatus.UNPROCESSABLE_CONTENT, "완성된 방문 일정만 공개할 수 있습니다."),
    ITINERARY_ALREADY_SHARED(HttpStatus.CONFLICT, "이미 공개된 일정입니다."),
    ITINERARY_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "본인의 일정만 공개할 수 있습니다."),
    REOPTIMIZATION_SOURCE_MISMATCH(HttpStatus.CONFLICT, "기준 일정이 요청한 여행에 속하지 않습니다."),
    REOPTIMIZATION_SOURCE_NOT_LATEST(HttpStatus.CONFLICT, "최신 일정 버전을 기준으로 다시 요청해 주세요."),
    INVALID_REOPTIMIZATION_STATE(HttpStatus.UNPROCESSABLE_CONTENT, "완료 일정 또는 현재 시각이 재최적화할 수 없는 상태입니다."),
    SHARED_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "공개 루트를 찾을 수 없습니다."),
    DUPLICATE_ROUTE_LIKE(HttpStatus.CONFLICT, "이미 좋아요한 루트입니다."),
    ROUTE_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "취소할 좋아요가 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 데이터 상태와 충돌합니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
