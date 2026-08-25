package com.routeplan.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    DUPLICATE_TRIP_PLACE(HttpStatus.CONFLICT, "여행에 이미 추가된 장소입니다."),
    TRIP_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "여행에 등록된 장소가 아닙니다."),
    TRIP_PLACE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "한 여행에는 장소를 최대 50개까지 추가할 수 있습니다."),
    TRIP_HAS_NO_PLACES(HttpStatus.UNPROCESSABLE_CONTENT, "최적화할 장소가 없습니다."),
    EXACT_SEARCH_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "Exact Search는 장소를 최대 10개까지 지원합니다."),
    ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
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
