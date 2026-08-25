package com.routeplan.optimization.route;

import com.routeplan.optimization.domain.Location;
import com.routeplan.trip.domain.TransportMode;
import java.util.List;

public interface RouteMatrixProvider {

    RouteMatrix build(List<Location> locations, TransportMode transportMode);
}
