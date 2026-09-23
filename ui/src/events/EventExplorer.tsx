import { useEffect, useState } from 'react'
import { WorldMap } from '../map/WorldMap'
import { fetchEvents } from './events'
import type { WorldEvent } from './events'
import './EventExplorer.css'

export function EventExplorer() {
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
      <WorldMap events={events} />
    </>
  )
}
