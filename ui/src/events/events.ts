export interface EventObservation {
  id: string
  text: string
  latitude: number
  longitude: number
  observedAt: string
  processingStatus: string
}

export interface WorldEvent {
  id: string
  title: string
  latitude: number
  longitude: number
  startedAt: string
  lastObservedAt: string
  observations: EventObservation[]
}

function isObservation(value: unknown): value is EventObservation {
  if (!value || typeof value !== 'object') return false
  const observation = value as Record<string, unknown>
  return typeof observation.id === 'string'
    && typeof observation.text === 'string'
    && typeof observation.latitude === 'number' && Number.isFinite(observation.latitude) && Math.abs(observation.latitude) <= 90
    && typeof observation.longitude === 'number' && Number.isFinite(observation.longitude) && Math.abs(observation.longitude) <= 180
    && typeof observation.observedAt === 'string' && Number.isFinite(Date.parse(observation.observedAt))
    && typeof observation.processingStatus === 'string'
}

function isWorldEvent(value: unknown): value is WorldEvent {
  if (!value || typeof value !== 'object') return false
  const event = value as Record<string, unknown>
  return typeof event.id === 'string'
    && typeof event.title === 'string'
    && typeof event.latitude === 'number' && Number.isFinite(event.latitude) && Math.abs(event.latitude) <= 90
    && typeof event.longitude === 'number' && Number.isFinite(event.longitude) && Math.abs(event.longitude) <= 180
    && typeof event.startedAt === 'string' && Number.isFinite(Date.parse(event.startedAt))
    && typeof event.lastObservedAt === 'string' && Number.isFinite(Date.parse(event.lastObservedAt))
    && Array.isArray(event.observations) && event.observations.every(isObservation)
}

export async function fetchEvents(signal: AbortSignal): Promise<WorldEvent[]> {
  const response = await fetch('/v1/events', { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(`Event request failed (${response.status})`)
  const data: unknown = await response.json()
  if (!Array.isArray(data) || !data.every(isWorldEvent)) {
    throw new Error('Unexpected event response')
  }
  return data
}
