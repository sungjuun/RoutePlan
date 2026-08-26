import { useEffect, useMemo } from 'react'
import L, { type LatLngBoundsExpression, type LatLngExpression } from 'leaflet'
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet'
import type { SharedRouteDetail } from '../types'

function BoundsController({ points }: { points: LatLngExpression[] }) {
  const map = useMap()
  useEffect(() => {
    if (points.length <= 1) {
      map.setView(points[0] ?? [37.5665, 126.978], 13, { animate: false })
      return
    }
    map.fitBounds(points as LatLngBoundsExpression, {
      padding: [36, 36],
      maxZoom: 15,
      animate: false,
    })
  }, [map, points])
  return null
}

function markerIcon(label: string, kind: 'stop' | 'hotel') {
  return L.divIcon({
    className: 'route-map-marker-wrap',
    html: `<span class="route-map-marker ${kind}"><b>${label}</b></span>`,
    iconSize: [34, 34],
    iconAnchor: [17, 17],
    popupAnchor: [0, -20],
  })
}

export function SharedRouteMap({ route }: { route: SharedRouteDetail }) {
  const accommodation = useMemo<LatLngExpression>(() => [
    Number(route.accommodationLatitude),
    Number(route.accommodationLongitude),
  ], [route.accommodationLatitude, route.accommodationLongitude])
  const stops = useMemo(() => route.items.map((item) => ({
    ...item,
    position: [Number(item.latitude), Number(item.longitude)] as LatLngExpression,
  })), [route.items])
  const points = useMemo(
    () => [accommodation, ...stops.map((stop) => stop.position)],
    [accommodation, stops],
  )
  const routeLine = [accommodation, ...stops.map((stop) => stop.position), accommodation]

  return (
    <div className="map-panel community-map">
      <MapContainer
        center={accommodation}
        zoom={13}
        scrollWheelZoom
        zoomAnimation={false}
        fadeAnimation={false}
        markerZoomAnimation={false}
        className="leaflet-map"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <BoundsController points={points} />
        <Marker position={accommodation} icon={markerIcon('H', 'hotel')}>
          <Popup><strong>{route.accommodationName}</strong><br />공개 당시 숙소</Popup>
        </Marker>
        {stops.map((stop) => (
          <Marker
            key={stop.itemId}
            position={stop.position}
            icon={markerIcon(String(stop.sequence), 'stop')}
          >
            <Popup><strong>{stop.placeName}</strong><br />{stop.sequence}번째 방문</Popup>
          </Marker>
        ))}
        {routeLine.length > 2 && (
          <Polyline
            positions={routeLine}
            pathOptions={{ color: '#e8795e', weight: 4, opacity: 0.85, dashArray: '8 9' }}
          />
        )}
      </MapContainer>
      <div className="map-legend">
        <span><i className="legend-hotel">H</i> 공개 당시 숙소</span>
        <span><i className="legend-route"></i> 방문 순서</span>
      </div>
    </div>
  )
}
