import { useEffect, useState } from 'react'
import { WorldMap } from '../map/WorldMap'
import { categoryOf, eventCategories, fetchEvents } from './events'
import type { EventCategory, WorldEvent } from './events'
import './EventExplorer.css'

export function EventExplorer() {
  const [categories, setCategories] = useState<EventCategory[]>(Object.keys(eventCategories) as EventCategory[])
  const [events, setEvents] = useState<WorldEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [request, setRequest] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    fetchEvents(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setEvents(data)
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [request])

  const visibleEvents = events.filter((event) => categories.includes(categoryOf(event)))

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
      <WorldMap events={visibleEvents} />
    </>
  )
}
