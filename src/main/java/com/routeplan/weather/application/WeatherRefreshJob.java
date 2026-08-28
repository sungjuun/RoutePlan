package com.routeplan.weather.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "routeplan.weather.auto-refresh-enabled", havingValue = "true", matchIfMissing = true)
public class WeatherRefreshJob {
    private final WeatherRefreshSettings settings;
    public WeatherRefreshJob(WeatherRefreshSettings settings) { this.settings = settings; }
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void refresh() { settings.refreshDue(); }
}
