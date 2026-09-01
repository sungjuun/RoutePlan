package com.routeplan.optimization.constraint;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "routeplan.optimization.time-dependent")
public class TimeDependentOptimizationProperties {

    private boolean enabled;
    private Duration bucket = Duration.ofHours(1);
    private int maxCandidates = 8;
    private int maxDays = 3;
    private int maxMatrixBuilds = 36;
    private int maxMatrixElements = 2_500;
    private int beamWidth = 128;
    private int maxEvaluatedStates = 250_000;
    private Duration maxSearchDuration = Duration.ofSeconds(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getBucket() { return bucket; }
    public void setBucket(Duration bucket) {
        if (bucket == null || bucket.isNegative() || bucket.isZero()
                || bucket.toSeconds() % 60 != 0
                || bucket.compareTo(Duration.ofMinutes(15)) < 0
                || bucket.compareTo(Duration.ofHours(4)) > 0) {
            throw new IllegalArgumentException("시간대별 최적화 버킷은 15분~4시간의 분 단위여야 합니다.");
        }
        this.bucket = bucket;
    }
    public int getMaxCandidates() { return maxCandidates; }
    public void setMaxCandidates(int value) { maxCandidates = bounded(value, 1, 15, "후보 수"); }
    public int getMaxDays() { return maxDays; }
    public void setMaxDays(int value) { maxDays = bounded(value, 1, 14, "여행일 수"); }
    public int getMaxMatrixBuilds() { return maxMatrixBuilds; }
    public void setMaxMatrixBuilds(int value) { maxMatrixBuilds = bounded(value, 1, 200, "Matrix 횟수"); }
    public int getMaxMatrixElements() { return maxMatrixElements; }
    public void setMaxMatrixElements(int value) { maxMatrixElements = bounded(value, 1, 100_000, "Matrix 요소 수"); }
    public int getBeamWidth() { return beamWidth; }
    public void setBeamWidth(int value) { beamWidth = bounded(value, 8, 10_000, "Beam 폭"); }
    public int getMaxEvaluatedStates() { return maxEvaluatedStates; }
    public void setMaxEvaluatedStates(int value) { maxEvaluatedStates = bounded(value, 100, 5_000_000, "탐색 상태 수"); }
    public Duration getMaxSearchDuration() { return maxSearchDuration; }
    public void setMaxSearchDuration(Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("전역 탐색 제한시간은 0초 초과 1분 이하여야 합니다.");
        }
        maxSearchDuration = value;
    }

    private int bounded(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + "는 " + minimum + "~" + maximum + " 범위여야 합니다.");
        }
        return value;
    }
}
