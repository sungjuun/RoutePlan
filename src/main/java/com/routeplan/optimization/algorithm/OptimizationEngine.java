package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.OptimizationAlgorithm;

public interface OptimizationEngine {

    OptimizationAlgorithm algorithm();

    OptimizationResult optimize(OptimizationRequest request);
}
