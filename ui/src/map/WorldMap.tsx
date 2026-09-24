import { useEffect, useRef, useState } from 'react'
import { Map, Marker, NavigationControl, Popup, setWorkerUrl } from 'maplibre-gl'
import { categoryOf, eventCategories } from '../events/events'
import type { ReportLocation } from '../observations/ObservationForm'
import type { WorldEvent } from '../events/events'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import 'maplibre-gl/dist/maplibre-gl.css'
import './WorldMap.css'

const mapStyle = import.meta.env.VITE_MAP_STYLE_URL || 'https://tiles.openfreemap.org/styles/liberty'

// Bundle MapLibre's worker and its imports for both Vite dev and production.
setWorkerUrl(workerUrl)

export function WorldMap({ events, onOpenEvent, selectedEventId, viewState, onViewStateChange, useCurrentLocation, selectingLocation, reportLocation, onPickLocation, onCancelSelection, onAddObservation }: {
  selectingLocation: boolean
  reportLocation: ReportLocation | null
  onPickLocation: (location: ReportLocation) => void
  onCancelSelection: () => void
  onAddObservation: (event: WorldEvent) => void
  events: WorldEvent[]
  onOpenEvent: (event: WorldEvent) => void
  selectedEventId: string | null
  viewState: { longitude: number; latitude: number; zoom: number }
  onViewStateChange: (viewState: { longitude: number; latitude: number; zoom: number }) => void
  useCurrentLocation: boolean
}) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<Map | null>(null)
  const selectingRef = useRef(false)
  useEffect(() => { selectingRef.current = selectingLocation }, [selectingLocation])
  const initialViewState = useRef(viewState)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!container.current) return

    let map: Map | undefined
    let resizeObserver: ResizeObserver | undefined
    let disposed = false
    let userMovedMap = false

    try {
      map = new Map({
        container: container.current,
        style: mapStyle,
        center: [initialViewState.current.longitude, initialViewState.current.latitude],
        zoom: initialViewState.current.zoom,
        renderWorldCopies: false,
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
      map.on('movestart', (event) => {
        if (event.originalEvent) userMovedMap = true
      })
      map.on('moveend', () => {
        const center = map?.getCenter()
        if (center && map) onViewStateChange({ longitude: center.lng, latitude: center.lat, zoom: map.getZoom() })
      })
      map.once('load', () => {
        setStatus('ready')
        if (!useCurrentLocation || !navigator.geolocation) return

        navigator.geolocation.getCurrentPosition(
          ({ coords }) => {
            // A delayed location result must not interrupt navigation or a new map instance.
            if (disposed || userMovedMap || selectingRef.current) return
            map?.jumpTo({ center: [coords.longitude, coords.latitude], zoom: 10 })
          },
          () => { /* Keep the world view when location is denied or unavailable. */ },
          { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 },
        )
      })
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
      disposed = true
      resizeObserver?.disconnect()
      map?.remove()
      mapRef.current = null
    }
  }, [attempt, onViewStateChange, useCurrentLocation])

  useEffect(() => {
    const map = mapRef.current
    if (!map || status !== 'ready' || !selectingLocation) return
    const canvas = map.getCanvas()
    canvas.style.cursor = 'crosshair'
    canvas.focus()
    const pick = (event: { lngLat: { lat: number; lng: number } }) => {
      onPickLocation({ latitude: event.lngLat.lat, longitude: ((event.lngLat.lng + 180) % 360 + 360) % 360 - 180 })
    }
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCancelSelection()
      if (event.key === 'Enter') {
        event.preventDefault()
        pick({ lngLat: map.getCenter() })
      }
    }
    map.on('click', pick)
    canvas.addEventListener('keydown', keyboard)
    return () => {
      canvas.style.cursor = ''
      map.off('click', pick)
      canvas.removeEventListener('keydown', keyboard)
    }
  }, [selectingLocation, status, attempt, onPickLocation, onCancelSelection])

  useEffect(() => {
    const map = mapRef.current
    if (!map || status !== 'ready' || !reportLocation) return
    const pin = new Marker({ color: '#18322d' }).setLngLat([reportLocation.longitude, reportLocation.latitude]).addTo(map)
    return () => { pin.remove() }
  }, [reportLocation, status, attempt])

  useEffect(() => {
    const map = mapRef.current
    if (!map || status !== 'ready') return

    let activePopup: Popup | undefined
    const markers = events.map((event) => {
      const position: [number, number] = [event.longitude, event.latitude]

      const button = document.createElement('button')
      button.type = 'button'
      button.className = 'event-marker'
      button.setAttribute('aria-label', `View event: ${event.title}`)
      const category = eventCategories[categoryOf(event)]
      button.style.backgroundColor = category.color
      button.setAttribute('aria-label', `View ${category.label.toLowerCase()} event: ${event.title}`)
      button.title = `${category.label}: ${event.title}`
      if (selectingLocation) button.setAttribute('aria-label', `Select location of ${event.title}`)

      // Use text nodes for report-derived content so titles cannot become HTML.
      const details = document.createElement('div')
      details.className = 'event-details'
      const heading = document.createElement('h2')
      heading.textContent = event.title
      details.append(heading)
      const savedSummary = event.summary?.trim() || event.generatedSummary?.summary.trim()
      if (savedSummary) {
        const summaryHeading = document.createElement('h3')
        summaryHeading.className = 'event-popup-summary-label'
        summaryHeading.textContent = 'AI summary'
        const summary = document.createElement('p')
        summary.className = 'event-popup-summary'
        summary.textContent = savedSummary
        details.append(summaryHeading, summary)
      }
      const expand = document.createElement('button')
      expand.type = 'button'
      expand.className = 'event-details-link'
      expand.textContent = 'Details'
      expand.addEventListener('click', (click) => {
        click.stopPropagation()
        popup.remove()
        onOpenEvent(event)
      })
      details.append(expand)
      const fields = [
        ['Category', category.label],
        ['Started', new Date(event.startedAt).toLocaleString()],
        ['Last observed', new Date(event.lastObservedAt).toLocaleString()],
        ['Coordinates', `${event.latitude.toFixed(5)}, ${event.longitude.toFixed(5)}`],
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
      const addObservation = document.createElement('button')
      addObservation.type = 'button'
      addObservation.className = 'event-add-observation'
      addObservation.textContent = 'Add observation'
      addObservation.addEventListener('click', (click) => {
        click.stopPropagation()
        popup.remove()
        onAddObservation(event)
      })
      const observationAction = document.createElement('div')
      observationAction.className = 'event-popup-observation-action'
      observationAction.append(addObservation)
      details.append(observationAction)
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
        if (selectingLocation) {
          onPickLocation({ latitude: event.latitude, longitude: event.longitude })
          return
        }
        if (popup.isOpen()) {
          popup.remove()
        } else {
          activePopup?.remove()
          popup.setLngLat(marker.getLngLat()).addTo(map)
          details.scrollTop = 0
          activePopup = popup
        }
      })
      if (!selectingLocation && event.id === selectedEventId) {
        popup.setLngLat(marker.getLngLat()).addTo(map)
        details.scrollTop = 0
        activePopup = popup
      }
      return marker
    })

    return () => {
      activePopup?.remove()
      markers.forEach((marker) => marker.remove())
    }
  }, [events, status, attempt, onOpenEvent, selectedEventId, selectingLocation, onPickLocation, onAddObservation])

  return (
    <section className="world-map" aria-label="Explore the world">
      <div className="world-map-canvas" ref={container} />
      {selectingLocation && status === 'ready' && <div className="map-message report-map-controls">
        <p role="status">Click a location to report an observation, or pan the map and select its center.</p>
        <div className="report-selection-actions">
          <button type="button" onClick={() => {
            const center = mapRef.current?.getCenter()
            if (center) onPickLocation({ latitude: center.lat, longitude: ((center.lng + 180) % 360 + 360) % 360 - 180 })
          }}>Use map center</button>
          <button type="button" onClick={onCancelSelection}>Cancel selection</button>
        </div>
      </div>}
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
