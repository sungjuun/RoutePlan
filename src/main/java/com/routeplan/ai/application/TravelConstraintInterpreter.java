package com.routeplan.ai.application;

import com.routeplan.ai.domain.TravelConstraints;

public interface TravelConstraintInterpreter {

    String providerName();

    TravelConstraints interpret(TravelInterpretationContext context);
}
