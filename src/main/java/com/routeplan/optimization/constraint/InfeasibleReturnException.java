package com.routeplan.optimization.constraint;

public class InfeasibleReturnException extends RuntimeException {

    public InfeasibleReturnException() {
        super("현재 위치에서 하루 종료 전 숙소로 돌아갈 수 없습니다.");
    }
}
