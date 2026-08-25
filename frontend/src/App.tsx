import { useCallback, useEffect, useMemo, useState } from 'react'
import { Compass, LoaderCircle, MapPinned, Route, Settings2 } from 'lucide-react'
import { ApiError, api } from './api/client'
import { AppHeader } from './components/AppHeader'
import { ItineraryWorkspace } from './components/ItineraryWorkspace'
import { PlaceWorkspace } from './components/PlaceWorkspace'
import { TripSetup } from './components/TripSetup'
import { Toast } from './components/Toast'
import { clearTripReference, loadWorkspace, saveWorkspace } from './lib/storage'
import type { Itinerary, Place, Trip, User } from './types'

type Section = 'places' | 'itinerary' | 'settings'

interface Notice {
  kind: 'success' | 'error' | 'info'
  message: string
}

export function App() {
  const initialWorkspace = useMemo(() => {
    const stored = loadWorkspace()
    const requestedTripId = Number(new URLSearchParams(window.location.search).get('tripId'))
    return Number.isSafeInteger(requestedTripId) && requestedTripId > 0
      ? { ...stored, tripId: requestedTripId }
      : stored
  }, [])
  const [user, setUser] = useState<User | null>(initialWorkspace.user)
  const [trip, setTrip] = useState<Trip | null>(null)
  const [itinerary, setItinerary] = useState<Itinerary | null>(null)
  const [previousItinerary, setPreviousItinerary] = useState<Itinerary | null>(null)
  const [itineraryPlaces, setItineraryPlaces] = useState<Record<number, Place>>({})
  const [section, setSection] = useState<Section>('places')
  const [booting, setBooting] = useState(initialWorkspace.tripId != null)
  const [notice, setNotice] = useState<Notice | null>(null)

  const notify = useCallback((kind: Notice['kind'], message: string) => {
    setNotice({ kind, message })
  }, [])

  const reportError = useCallback((error: unknown) => {
    if (error instanceof ApiError) {
      const details = error.body.violations?.map((violation) => violation.message).join(' · ')
      notify('error', details ? `${error.message} — ${details}` : error.message)
      return
    }
    notify('error', error instanceof Error ? error.message : '예상하지 못한 오류가 발생했습니다.')
  }, [notify])

  const loadItineraryContext = useCallback(async (latest: Itinerary | null) => {
    setItinerary(latest)
    if (!latest) {
      setPreviousItinerary(null)
      setItineraryPlaces({})
      return
    }

    const [parent, places] = await Promise.all([
      latest.parentItineraryId ? api.getItinerary(latest.parentItineraryId) : Promise.resolve(null),
      Promise.all(
        [...new Set(latest.items.map((item) => item.placeId))].map((placeId) =>
          api.getPlace(placeId),
        ),
      ),
    ])
    setPreviousItinerary(parent)
    setItineraryPlaces(Object.fromEntries(places.map((place) => [place.id, place])))
  }, [])

  useEffect(() => {
    if (initialWorkspace.tripId == null) return
    let cancelled = false
    const restore = async () => {
      try {
        const restoredTrip = await api.getTrip(initialWorkspace.tripId!)
        if (cancelled) return
        setTrip(restoredTrip)
        if (!initialWorkspace.user || initialWorkspace.user.id !== restoredTrip.userId) {
          const recoveredUser: User = {
            id: restoredTrip.userId,
            nickname: `여행자 ${restoredTrip.userId}`,
            createdAt: restoredTrip.createdAt,
          }
          setUser(recoveredUser)
          saveWorkspace({ user: recoveredUser, tripId: restoredTrip.id })
        }
        try {
          const latest = await api.getLatestItinerary(restoredTrip.id)
          if (!cancelled) await loadItineraryContext(latest)
        } catch (error) {
          if (!(error instanceof ApiError) || error.body.code !== 'ITINERARY_NOT_FOUND') throw error
        }
      } catch (error) {
        if (!cancelled) {
          clearTripReference(initialWorkspace.user)
          reportError(error)
        }
      } finally {
        if (!cancelled) setBooting(false)
      }
    }
    void restore()
    return () => {
      cancelled = true
    }
  }, [initialWorkspace.tripId, initialWorkspace.user, loadItineraryContext, reportError])

  const handleTripReady = (nextUser: User, nextTrip: Trip) => {
    setUser(nextUser)
    setTrip(nextTrip)
    setItinerary(null)
    setPreviousItinerary(null)
    setItineraryPlaces({})
    saveWorkspace({ user: nextUser, tripId: nextTrip.id })
    setSection('places')
    notify('success', `${nextTrip.name} 작업공간을 만들었습니다.`)
  }

  const handleTripUpdated = (nextTrip: Trip) => {
    setTrip(nextTrip)
    notify('success', '여행 설정을 저장했습니다.')
  }

  const handleTripChanged = (nextTrip: Trip, message?: string) => {
    setTrip(nextTrip)
    if (message) notify('success', message)
  }

  const handleItineraryChanged = async (next: Itinerary, message: string) => {
    await loadItineraryContext(next)
    setSection('itinerary')
    notify('success', message)
  }

  const handleNewTrip = () => {
    clearTripReference(user)
    setTrip(null)
    setItinerary(null)
    setPreviousItinerary(null)
    setItineraryPlaces({})
    setSection('places')
  }

  if (booting) {
    return (
      <main className="app-loading" aria-live="polite">
        <div className="brand-mark"><Route size={25} /></div>
        <LoaderCircle className="spin" size={25} />
        <p>여행 작업공간을 불러오는 중입니다</p>
      </main>
    )
  }

  if (!trip) {
    return (
      <>
        <TripSetup
          user={user}
          onReady={handleTripReady}
          onUserCreated={(nextUser) => {
            setUser(nextUser)
            saveWorkspace({ user: nextUser, tripId: null })
          }}
          onError={reportError}
        />
        <Toast notice={notice} onClose={() => setNotice(null)} />
      </>
    )
  }

  return (
    <div className="app-shell">
      <AppHeader trip={trip} user={user} onNewTrip={handleNewTrip} />
      <div className="workspace-shell">
        <nav className="workspace-nav" aria-label="여행 작업 단계">
          <button
            className={section === 'places' ? 'active' : ''}
            onClick={() => setSection('places')}
          >
            <span><MapPinned size={19} /></span>
            <span><strong>장소 담기</strong><small>{trip.places.length}곳 선택됨</small></span>
          </button>
          <button
            className={section === 'itinerary' ? 'active' : ''}
            onClick={() => setSection('itinerary')}
          >
            <span><Compass size={19} /></span>
            <span><strong>일정 보기</strong><small>{itinerary ? `버전 ${itinerary.version}` : '계산 전'}</small></span>
          </button>
          <button
            className={section === 'settings' ? 'active' : ''}
            onClick={() => setSection('settings')}
          >
            <span><Settings2 size={19} /></span>
            <span><strong>여행 설정</strong><small>시간·숙소·이동</small></span>
          </button>
        </nav>

        <main className="workspace-main">
          {section === 'places' && (
            <PlaceWorkspace
              trip={trip}
              hasItinerary={itinerary != null}
              onTripChanged={handleTripChanged}
              onGoToItinerary={() => setSection('itinerary')}
              onError={reportError}
            />
          )}
          {section === 'itinerary' && (
            <ItineraryWorkspace
              trip={trip}
              itinerary={itinerary}
              previousItinerary={previousItinerary}
              itineraryPlaces={itineraryPlaces}
              onItineraryChanged={handleItineraryChanged}
              onError={reportError}
              onGoToPlaces={() => setSection('places')}
            />
          )}
          {section === 'settings' && (
            <TripSetup
              user={user}
              trip={trip}
              embedded
              onUpdated={handleTripUpdated}
              onError={reportError}
            />
          )}
        </main>
      </div>
      <Toast notice={notice} onClose={() => setNotice(null)} />
    </div>
  )
}
