package com.routeplan.integration;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "routeplan.external.usage")
public class ExternalUsageProperties {
    private int warningPercent = 80;
    private long openAiMonthlyRequestLimit = 500;
    private long openAiMonthlyTokenLimit = 1_000_000;
    private BigDecimal googlePlacesUsdPerThousand = BigDecimal.ZERO;
    private BigDecimal googlePlaceDetailsUsdPerThousand = BigDecimal.ZERO;
    private BigDecimal googleRoutesUsdPerThousand = BigDecimal.ZERO;
    private BigDecimal googleGeometryUsdPerThousand = BigDecimal.ZERO;
    private BigDecimal openAiInputUsdPerMillion = BigDecimal.ZERO;
    private BigDecimal openAiOutputUsdPerMillion = BigDecimal.ZERO;

    public void validate() {
        if (warningPercent < 1 || warningPercent > 100) {
            throw new IllegalArgumentException("외부 API 사용량 경고 기준은 1~100이어야 합니다.");
        }
        if (openAiMonthlyRequestLimit < 0 || openAiMonthlyTokenLimit < 0) {
            throw new IllegalArgumentException("OpenAI 월별 한도는 음수일 수 없습니다.");
        }
        if (googlePlacesUsdPerThousand.signum() < 0
                || googlePlaceDetailsUsdPerThousand.signum() < 0
                || googleRoutesUsdPerThousand.signum() < 0
                || googleGeometryUsdPerThousand.signum() < 0
                || openAiInputUsdPerMillion.signum() < 0
                || openAiOutputUsdPerMillion.signum() < 0) {
            throw new IllegalArgumentException("외부 API 비용 단가는 음수일 수 없습니다.");
        }
    }

    public int getWarningPercent() { return warningPercent; }
    public void setWarningPercent(int value) { warningPercent = value; }
    public long getOpenAiMonthlyRequestLimit() { return openAiMonthlyRequestLimit; }
    public void setOpenAiMonthlyRequestLimit(long value) { openAiMonthlyRequestLimit = value; }
    public long getOpenAiMonthlyTokenLimit() { return openAiMonthlyTokenLimit; }
    public void setOpenAiMonthlyTokenLimit(long value) { openAiMonthlyTokenLimit = value; }
    public BigDecimal getGooglePlacesUsdPerThousand() { return googlePlacesUsdPerThousand; }
    public void setGooglePlacesUsdPerThousand(BigDecimal value) { googlePlacesUsdPerThousand = value; }
    public BigDecimal getGooglePlaceDetailsUsdPerThousand() { return googlePlaceDetailsUsdPerThousand; }
    public void setGooglePlaceDetailsUsdPerThousand(BigDecimal value) { googlePlaceDetailsUsdPerThousand = value; }
    public BigDecimal getGoogleRoutesUsdPerThousand() { return googleRoutesUsdPerThousand; }
    public void setGoogleRoutesUsdPerThousand(BigDecimal value) { googleRoutesUsdPerThousand = value; }
    public BigDecimal getGoogleGeometryUsdPerThousand() { return googleGeometryUsdPerThousand; }
    public void setGoogleGeometryUsdPerThousand(BigDecimal value) { googleGeometryUsdPerThousand = value; }
    public BigDecimal getOpenAiInputUsdPerMillion() { return openAiInputUsdPerMillion; }
    public void setOpenAiInputUsdPerMillion(BigDecimal value) { openAiInputUsdPerMillion = value; }
    public BigDecimal getOpenAiOutputUsdPerMillion() { return openAiOutputUsdPerMillion; }
    public void setOpenAiOutputUsdPerMillion(BigDecimal value) { openAiOutputUsdPerMillion = value; }
}
