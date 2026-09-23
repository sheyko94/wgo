import { useEffect, useRef, useState } from 'react'
import { LngLatBounds, Map, Marker, NavigationControl, Popup, setWorkerUrl } from 'maplibre-gl'
import type { WorldEvent } from '../events/events'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import 'maplibre-gl/dist/maplibre-gl.css'
import './WorldMap.css'

const mapStyle = import.meta.env.VITE_MAP_STYLE_URL || 'https://tiles.openfreemap.org/styles/liberty'

// Bundle MapLibre's worker and its imports for both Vite dev and production.
setWorkerUrl(workerUrl)

export function WorldMap({ events }: { events: WorldEvent[] }) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<Map | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!container.current) return

    let map: Map | undefined
    let resizeObserver: ResizeObserver | undefined

    try {
      map = new Map({
        container: container.current,
        style: mapStyle,
        center: [10, 35],
        zoom: 2,
        pitch: 0,
        maxPitch: 0,
        dragRotate: false,
        touchZoomRotate: true,
        attributionControl: { compact: false },
      })
      mapRef.current = map
      map.touchZoomRotate.disableRotation()
      map.keyboard.disableRotation()
      map.addControl(new NavigationControl({ showCompass: false }), 'top-right')
      map.on('load', () => setStatus('ready'))
      map.on('error', () => setStatus('error'))
      map.getCanvas().setAttribute('aria-label', 'World map. Use arrow keys to pan and plus or minus to zoom.')

      resizeObserver = new ResizeObserver(() => map?.resize())
      resizeObserver.observe(container.current)
    } catch {
      // Map initialization can fail synchronously when WebGL is unavailable.
      // oxlint-disable-next-line react/set-state-in-effect
      setStatus('error')
    }

    return () => {
      resizeObserver?.disconnect()
      map?.remove()
      mapRef.current = null
    }
  }, [attempt])

  useEffect(() => {
    const map = mapRef.current
    if (!map || status !== 'ready') return

    const bounds = new LngLatBounds()
    let activePopup: Popup | undefined
    const markers = events.map((event) => {
      const position: [number, number] = [event.longitude, event.latitude]
      bounds.extend(position)

      const button = document.createElement('button')
      button.type = 'button'
      button.className = 'event-marker'
      button.setAttribute('aria-label', `View event: ${event.title}`)
      button.title = event.title

      // Use text nodes for report-derived content so titles cannot become HTML.
      const details = document.createElement('div')
      details.className = 'event-details'
      const heading = document.createElement('h2')
      heading.textContent = event.title
      details.append(heading)
      const fields = [
        ['Started', new Date(event.startedAt).toLocaleString()],
        ['Last observed', new Date(event.lastObservedAt).toLocaleString()],
        ['Coordinates', `${event.latitude.toFixed(5)}, ${event.longitude.toFixed(5)}`],
        ['Observations', String(event.observations.length)],
        ['Event ID', event.id],
      ]
      const list = document.createElement('dl')
      for (const [label, value] of fields) {
        const term = document.createElement('dt')
        term.textContent = label
        const description = document.createElement('dd')
        description.textContent = value
        list.append(term, description)
      }
      details.append(list)
      const observationsHeading = document.createElement('h3')
      observationsHeading.textContent = `Observations (${event.observations.length})`
      details.append(observationsHeading)
      const observationsList = document.createElement('ul')
      observationsList.className = 'event-observations'
      for (const observation of [...event.observations].sort((a, b) => Date.parse(b.observedAt) - Date.parse(a.observedAt))) {
        const item = document.createElement('li')
        const text = document.createElement('p')
        text.className = 'observation-text'
        text.textContent = observation.text
        const time = document.createElement('time')
        time.dateTime = observation.observedAt
        time.textContent = new Date(observation.observedAt).toLocaleString()
        const location = document.createElement('p')
        location.className = 'observation-location'
        location.textContent = `Coordinates: ${observation.latitude.toFixed(5)}, ${observation.longitude.toFixed(5)}`
        item.append(text, time, location)
        observationsList.append(item)
      }
      if (event.observations.length === 0) {
        const empty = document.createElement('p')
        empty.textContent = 'No observations linked to this event yet.'
        details.append(empty)
      } else {
        details.append(observationsList)
      }
      const popup = new Popup({ offset: 16, maxWidth: '320px' }).setDOMContent(details)
      const marker = new Marker({ element: button }).setLngLat(position).addTo(map)
      // Native button activation supports mouse, Enter, and Space consistently.
      button.addEventListener('click', (click) => {
        click.stopPropagation()
        if (popup.isOpen()) {
          popup.remove()
        } else {
          activePopup?.remove()
          popup.setLngLat(marker.getLngLat()).addTo(map)
          activePopup = popup
        }
      })
      return marker
    })
    if (events.length > 0) {
      map.fitBounds(bounds, { padding: 60, maxZoom: 12, duration: 0 })
    }

    return () => {
      activePopup?.remove()
      markers.forEach((marker) => marker.remove())
    }
  }, [events, status, attempt])

  return (
    <section className="world-map" aria-label="Explore the world">
      <div className="world-map-canvas" ref={container} />
      {status === 'loading' && (
        <p className="map-message" role="status">Loading map…</p>
      )}
      {status === 'error' && (
        <div className="map-message" role="alert">
          <p>We couldn’t load the complete map. Check your connection and try again.</p>
          <button type="button" onClick={() => {
            setStatus('loading')
            setAttempt((value) => value + 1)
          }}>Retry map</button>
        </div>
      )}
    </section>
  )
}
