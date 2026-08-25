package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.route.RouteProvider;

public interface OptimizationEngine {

    OptimizationAlgorithm algorithm();

    OptimizationResult optimize(OptimizationRequest request);

    OptimizationResult optimize(OptimizationRequest request, RouteProvider routeProvider);
}
