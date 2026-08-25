package com.routeplan.optimization.route;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.trip.domain.TransportMode;

public interface RouteProvider {

    RouteResult getRoute(Location origin, Location destination, TransportMode mode);
}
