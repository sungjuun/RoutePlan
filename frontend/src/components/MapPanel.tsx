import { useEffect, useMemo, useState } from 'react'
import L, { type LatLngBoundsExpression, type LatLngExpression } from 'leaflet'
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet'
import type { Itinerary, Place, Trip } from '../types'

interface Props {
  trip: Trip
  itinerary?: Itinerary | null
  places?: Record<number, Place>
  compact?: boolean
}

function BoundsController({ points }: { points: LatLngExpression[] }) {
  const map = useMap()
  useEffect(() => {
    if (points.length <= 1) {
      map.setView(points[0] ?? [37.5665, 126.978], 13, { animate: false })
      return
    }
    map.fitBounds(points as LatLngBoundsExpression, { padding: [44, 44], maxZoom: 15, animate: false })
  }, [map, points])
  return null
}

function markerIcon(label: string, kind: 'stop' | 'hotel' | 'completed') {
  return L.divIcon({
    className: 'route-map-marker-wrap',
    html: `<span class="route-map-marker ${kind}"><b>${label}</b></span>`,
    iconSize: [34, 34],
    iconAnchor: [17, 17],
    popupAnchor: [0, -20],
  })
}

export function MapPanel({ trip, itinerary, places = {}, compact = false }: Props) {
  const [selectedDay, setSelectedDay] = useState(1)
  const activeDay = itinerary?.days.some((day) => day.dayNumber === selectedDay) ? selectedDay : 1
  const selectedDate = itinerary?.days.find((day) => day.dayNumber === activeDay)?.visitDate
  const accommodation = useMemo<LatLngExpression>(() => [
    Number(trip.accommodationLatitude),
    Number(trip.accommodationLongitude),
  ], [trip.accommodationLatitude, trip.accommodationLongitude])
  const stops = useMemo(() => {
    if (itinerary) {
      return itinerary.items
        .filter((item) => !selectedDate || item.visitDate === selectedDate)
        .flatMap((item, index) => {
        const place = places[item.placeId] ?? trip.places.find((candidate) => candidate.placeId === item.placeId)
        return place ? [{
          id: item.placeId,
          name: item.placeName,
          position: [Number(place.latitude), Number(place.longitude)] as LatLngExpression,
          sequence: selectedDate ? index + 1 : item.sequence,
          completed: item.status === 'COMPLETED',
        }] : []
        })
    }
    return trip.places.map((place, index) => ({
      id: place.placeId,
      name: place.name,
      position: [Number(place.latitude), Number(place.longitude)] as LatLngExpression,
      sequence: index + 1,
      completed: false,
    }))
  }, [itinerary, places, selectedDate, trip.places])

  const allPoints = useMemo(
    () => [accommodation, ...stops.map((stop) => stop.position)],
    [accommodation, stops],
  )
  const routeLine = itinerary && stops.length > 0
    ? [accommodation, ...stops.map((stop) => stop.position), accommodation]
    : stops.map((stop) => stop.position)

  return (
    <div className={`map-panel ${compact ? 'map-panel-compact' : ''}`}>
      {itinerary && itinerary.days.length > 1 && (
        <div className="map-day-switcher" aria-label="지도 표시 일자">
          {itinerary.days.map((day) => (
            <button key={day.dayNumber} className={activeDay === day.dayNumber ? 'active' : ''} onClick={() => setSelectedDay(day.dayNumber)}>DAY {day.dayNumber}</button>
          ))}
        </div>
      )}
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
        <BoundsController points={allPoints} />
        <Marker position={accommodation} icon={markerIcon('H', 'hotel')}>
          <Popup><strong>{trip.accommodationName}</strong><br />출발하고 돌아올 숙소</Popup>
        </Marker>
        {stops.map((stop) => (
          <Marker
            key={`${stop.id}-${stop.sequence}`}
            position={stop.position}
            icon={markerIcon(String(stop.sequence), stop.completed ? 'completed' : 'stop')}
          >
            <Popup><strong>{stop.name}</strong><br />{stop.sequence}번째 방문</Popup>
          </Marker>
        ))}
        {routeLine.length > 1 && (
          <Polyline positions={routeLine} pathOptions={{ color: '#e8795e', weight: 4, opacity: 0.85, dashArray: '8 9' }} />
        )}
      </MapContainer>
      <div className="map-legend">
        <span><i className="legend-hotel">H</i> 숙소</span>
        <span><i className="legend-route"></i> {itinerary ? (selectedDate ? `DAY ${activeDay} 일정` : '일정 순서') : '선택한 장소'}</span>
      </div>
    </div>
  )
}
