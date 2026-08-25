package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.OptimizationAlgorithm;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OptimizationEngineRegistry {

    private final Map<OptimizationAlgorithm, OptimizationEngine> engines;

    public OptimizationEngineRegistry(List<OptimizationEngine> engines) {
        EnumMap<OptimizationAlgorithm, OptimizationEngine> registered =
                new EnumMap<>(OptimizationAlgorithm.class);
        for (OptimizationEngine engine : engines) {
            OptimizationEngine previous = registered.put(engine.algorithm(), engine);
            if (previous != null) {
                throw new IllegalStateException("중복 최적화 엔진: " + engine.algorithm());
            }
        }
        this.engines = Map.copyOf(registered);
    }

    public OptimizationEngine get(OptimizationAlgorithm algorithm) {
        OptimizationEngine engine = engines.get(algorithm);
        if (engine == null) {
            throw new IllegalArgumentException("지원하지 않는 최적화 알고리즘입니다: " + algorithm);
        }
        return engine;
    }
}
