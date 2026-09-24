import { useCallback, useEffect, useState } from 'react'
import { WorldMap } from '../map/WorldMap'
import { eventCategories, eventStatuses, fetchEvents } from './events'
import type { EventCategory, EventStatus, WorldEvent } from './events'
import { EventDetails } from './EventDetails'
import { EventSearch } from './EventSearch'
import { ObservationForm } from '../observations/ObservationForm'
import type { ReportDraft, ReportLocation } from '../observations/ObservationForm'
import './EventExplorer.css'

function eventIdFromUrl() {
  const params = new URLSearchParams(window.location.search)
  const pathMatch = window.location.pathname.match(/^\/event\/([^/]+)$/)
  return pathMatch?.[1] ?? params.get('event')
}

export function EventExplorer() {
  const [selectingLocation, setSelectingLocation] = useState(false)
  const [reportDraft, setReportDraft] = useState<ReportDraft | null>(null)
  const [submittedObservation, setSubmittedObservation] = useState<string | null>(null)
  const [categories, setCategories] = useState<EventCategory[]>(Object.keys(eventCategories) as EventCategory[])
  const [statuses, setStatuses] = useState<EventStatus[]>(Object.keys(eventStatuses) as EventStatus[])
  const [events, setEvents] = useState<WorldEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [request, setRequest] = useState(0)
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedEvent, setSelectedEvent] = useState<WorldEvent | null>(null)
  const [mapEventId, setMapEventId] = useState(eventIdFromUrl)
  const [mapViewState, setMapViewState] = useState(() => {
    const params = new URLSearchParams(window.location.search)
    const longitude = Number(params.get('longitude'))
    const latitude = Number(params.get('latitude'))
    const zoom = Number(params.get('zoom'))
    return Number.isFinite(longitude) && Number.isFinite(latitude) && Number.isFinite(zoom)
      ? { longitude, latitude, zoom }
      : { longitude: 10, latitude: 35, zoom: 2 }
  })
  const pickLocation = useCallback((location: ReportLocation) => {
    setSelectingLocation(false)
    setReportDraft({ location })
  }, [])
  const cancelSelection = useCallback(() => setSelectingLocation(false), [])
  const addObservation = useCallback((event: WorldEvent) => {
    setSelectingLocation(false)
    setMapEventId(null)
    setReportDraft({ event, location: { latitude: event.latitude, longitude: event.longitude } })
    setSubmittedObservation(null)
  }, [])
  const detailsPath = /^\/event\/[^/]+$/.test(window.location.pathname)
  const hasUrlViewState = new URLSearchParams(window.location.search).has('longitude')
    && new URLSearchParams(window.location.search).has('latitude')
    && new URLSearchParams(window.location.search).has('zoom')

  useEffect(() => {
    const controller = new AbortController()
    fetchEvents(controller.signal, searchQuery, categories, statuses)
      .then((data) => {
        if (!controller.signal.aborted) {
          setEvents(data)
          const eventFromUrl = eventIdFromUrl() && data.find((event) => event.id === eventIdFromUrl())
          if (eventFromUrl) {
            if (detailsPath) setSelectedEvent(eventFromUrl)
          }
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [request, detailsPath, searchQuery, categories, statuses])

  useEffect(() => {
    const url = new URL(window.location.href)
    if (detailsPath) {
      url.searchParams.delete('event')
    } else if (mapEventId) {
      url.searchParams.set('event', mapEventId)
    } else {
      url.searchParams.delete('event')
    }
    url.searchParams.set('longitude', mapViewState.longitude.toFixed(5))
    url.searchParams.set('latitude', mapViewState.latitude.toFixed(5))
    url.searchParams.set('zoom', mapViewState.zoom.toFixed(2))
    window.history.replaceState(null, '', url)
  }, [detailsPath, mapEventId, mapViewState])

  function reloadFilters() {
    setLoading(true)
    setFailed(false)
    setEvents([])
    setMapEventId(null)
  }

  const openEvent = useCallback((event: WorldEvent) => {
    setMapEventId(event.id)
    setSelectedEvent(event)
    const url = new URL(window.location.href)
    url.pathname = `/event/${event.id}`
    url.searchParams.delete('event')
    window.history.pushState(null, '', url)
  }, [])

  if (selectedEvent) {
    const currentEvent = events.find((event) => event.id === selectedEvent.id) ?? selectedEvent
    return <EventDetails key={currentEvent.id} event={currentEvent} onEventUpdated={(updated) => {
      setEvents((previous) => previous.map((event) => event.id === updated.id ? updated : event))
      setSelectedEvent(updated)
    }} onBack={() => {
      const url = new URL(window.location.href)
      url.pathname = '/'
      url.searchParams.set('event', currentEvent.id)
      window.history.replaceState(null, '', url)
      setSelectedEvent(null)
    }} />
  }

  return (
    <>
      <div className="events-toolbar">
        <p role="status">
          {loading ? 'Loading events…' : failed && events.length === 0
            ? 'Events unavailable'
            : `${events.length} ${events.length === 1 ? 'event' : 'events'} loaded`}
        </p>
        <div className="events-toolbar-actions">
        <button type="button" disabled={selectingLocation || reportDraft !== null} onClick={() => {
          setSubmittedObservation(null)
          setMapEventId(null)
          setSelectingLocation(true)
        }}>Report observation</button>
        <button type="button" disabled={loading} onClick={() => {
          setLoading(true)
          setFailed(false)
          setRequest((value) => value + 1)
        }}>{loading ? 'Loading…' : 'Refresh events'}</button>
        </div>
      </div>
      <EventSearch activeQuery={searchQuery} onQuery={(query) => {
        setSearchQuery(query)
        reloadFilters()
      }} />
      {failed && <p className="events-error" role="alert">
        Couldn’t load events. Try refreshing.
        {events.length > 0 && ' Previously loaded events are still shown.'}
      </p>}
      {!loading && !failed && events.length === 0 && (
        <p>No events match your search and filters. Try different text or select more categories and statuses.</p>
      )}
      <fieldset className="category-filters">
        <legend>Event categories</legend>
        <div className="category-options">
          {(Object.keys(eventCategories) as EventCategory[]).map((category) => (
            <label key={category}>
              <input type="checkbox" checked={categories.includes(category)} onChange={(change) => {
                reloadFilters()
                setCategories((selected) => change.target.checked
                  ? [...selected, category] : selected.filter((value) => value !== category))
              }} />
              <span className="category-swatch" style={{ backgroundColor: eventCategories[category].color }} aria-hidden="true" />
              {eventCategories[category].label}
            </label>
          ))}
        </div>
        <button type="button" onClick={() => {
          reloadFilters()
          setCategories(Object.keys(eventCategories) as EventCategory[])
        }}>Show all categories</button>
      </fieldset>
      <fieldset className="category-filters">
        <legend>Event status</legend>
        <div className="category-options">
          {(Object.keys(eventStatuses) as EventStatus[]).map((status) => (
            <label key={status}>
              <input type="checkbox" checked={statuses.includes(status)} onChange={(change) => {
                reloadFilters()
                setStatuses((selected) => change.target.checked
                  ? [...selected, status] : selected.filter((value) => value !== status))
              }} />
              {eventStatuses[status]}
            </label>
          ))}
        </div>
        <button type="button" onClick={() => {
          reloadFilters()
          setStatuses(Object.keys(eventStatuses) as EventStatus[])
        }}>Show all statuses</button>
      </fieldset>
      {selectingLocation && <div className="report-map-controls">
          <p role="status">Select a location on the map. Press Enter on the map to use its center, or Escape to cancel.</p>
          <button type="button" onClick={cancelSelection}>Cancel reporting</button>
      </div>}
      {submittedObservation && <p role="status" className="report-confirmation">
        Observation submitted ({submittedObservation}). Processing may take a moment. Refresh events to see it; your current filters may hide the event.
      </p>}
      {reportDraft && <ObservationForm draft={reportDraft} onCancel={() => setReportDraft(null)} onSubmitted={(id) => {
        setReportDraft(null)
        setSubmittedObservation(id)
      }} />}
      <WorldMap
        selectingLocation={selectingLocation}
        reportLocation={reportDraft?.location ?? null}
        onPickLocation={pickLocation}
        onCancelSelection={cancelSelection}
        onAddObservation={addObservation}
        events={events}
        selectedEventId={mapEventId}
        viewState={mapViewState}
        onViewStateChange={setMapViewState}
        useCurrentLocation={!hasUrlViewState}
        onOpenEvent={openEvent}
      />
    </>
  )
}
