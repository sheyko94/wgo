import { useEffect, useState } from 'react'
import { WorldMap } from '../map/WorldMap'
import { categoryOf, eventCategories, fetchEvents } from './events'
import type { EventCategory, WorldEvent } from './events'
import { EventDetails } from './EventDetails'
import './EventExplorer.css'

export function EventExplorer() {
  const [categories, setCategories] = useState<EventCategory[]>(Object.keys(eventCategories) as EventCategory[])
  const [events, setEvents] = useState<WorldEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [request, setRequest] = useState(0)
  const [selectedEvent, setSelectedEvent] = useState<WorldEvent | null>(null)
  const [mapEventId, setMapEventId] = useState(() => new URLSearchParams(window.location.search).get('event'))
  const [mapViewState, setMapViewState] = useState(() => {
    const params = new URLSearchParams(window.location.search)
    const longitude = Number(params.get('longitude'))
    const latitude = Number(params.get('latitude'))
    const zoom = Number(params.get('zoom'))
    return Number.isFinite(longitude) && Number.isFinite(latitude) && Number.isFinite(zoom)
      ? { longitude, latitude, zoom }
      : { longitude: 10, latitude: 35, zoom: 2 }
  })
  const hasUrlViewState = new URLSearchParams(window.location.search).has('longitude')
    && new URLSearchParams(window.location.search).has('latitude')
    && new URLSearchParams(window.location.search).has('zoom')

  useEffect(() => {
    const controller = new AbortController()
    fetchEvents(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setEvents(data)
          const eventFromUrl = new URLSearchParams(window.location.search).get('event')
            && data.find((event) => event.id === new URLSearchParams(window.location.search).get('event'))
          if (eventFromUrl) setSelectedEvent(eventFromUrl)
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [request])

  useEffect(() => {
    const url = new URL(window.location.href)
    if (mapEventId) url.searchParams.set('event', mapEventId)
    else url.searchParams.delete('event')
    url.searchParams.set('longitude', mapViewState.longitude.toFixed(5))
    url.searchParams.set('latitude', mapViewState.latitude.toFixed(5))
    url.searchParams.set('zoom', mapViewState.zoom.toFixed(2))
    window.history.replaceState(null, '', url)
  }, [mapEventId, mapViewState])

  const visibleEvents = events.filter((event) => categories.includes(categoryOf(event)))

  if (selectedEvent) {
    const currentEvent = events.find((event) => event.id === selectedEvent.id) ?? selectedEvent
    return <EventDetails event={currentEvent} onBack={() => setSelectedEvent(null)} />
  }

  return (
    <>
      <div className="events-toolbar">
        <p role="status">
          {loading ? 'Loading events…' : failed && events.length === 0
            ? 'Events unavailable'
            : `${events.length} ${events.length === 1 ? 'event' : 'events'} loaded`}
        </p>
        <button type="button" disabled={loading} onClick={() => {
          setLoading(true)
          setFailed(false)
          setRequest((value) => value + 1)
        }}>{loading ? 'Loading…' : 'Refresh events'}</button>
      </div>
      {failed && <p className="events-error" role="alert">
        Couldn’t load events. Try refreshing.
        {events.length > 0 && ' Previously loaded events are still shown.'}
      </p>}
      {!loading && !failed && events.length === 0 && (
        <p>No events yet. Events appear after observations are processed. Refresh to check again.</p>
      )}
      <fieldset className="category-filters">
        <legend>Event categories</legend>
        <div className="category-options">
          {(Object.keys(eventCategories) as EventCategory[]).map((category) => (
            <label key={category}>
              <input type="checkbox" checked={categories.includes(category)} onChange={(change) => {
                setCategories((selected) => change.target.checked
                  ? [...selected, category] : selected.filter((value) => value !== category))
              }} />
              <span className="category-swatch" style={{ backgroundColor: eventCategories[category].color }} aria-hidden="true" />
              {eventCategories[category].label}
              <span>({events.filter((event) => categoryOf(event) === category).length})</span>
            </label>
          ))}
        </div>
        <button type="button" onClick={() => setCategories(Object.keys(eventCategories) as EventCategory[])}>Show all</button>
      </fieldset>
      {events.length > 0 && <p role="status">
        {visibleEvents.length === 0 ? 'No events match the selected categories.' : `${visibleEvents.length} of ${events.length} events shown`}
      </p>}
      <WorldMap
        events={visibleEvents}
        selectedEventId={mapEventId}
        viewState={mapViewState}
        onViewStateChange={setMapViewState}
        useCurrentLocation={!hasUrlViewState}
        onOpenEvent={(event) => {
          setMapEventId(event.id)
          setSelectedEvent(event)
        }}
      />
    </>
  )
}
