import type { WorldEvent } from './events'
import { categoryOf, eventCategories } from './events'

interface EventDetailsProps {
  event: WorldEvent
  onBack: () => void
}

export function EventDetails({ event, onBack }: EventDetailsProps) {
  const category = eventCategories[categoryOf(event)]
  const observations = [...event.observations].sort(
    (a, b) => Date.parse(b.observedAt) - Date.parse(a.observedAt),
  )

  return (
    <section className="event-details-page" aria-labelledby="event-details-title">
      <button type="button" className="back-button" onClick={onBack}>← Back to map</button>
      <div className="event-details-heading">
        <div>
          <p className="eyebrow">Event details</p>
          <h2 id="event-details-title">{event.title}</h2>
        </div>
        <span className="event-category-badge" style={{ backgroundColor: category.color }}>{category.label}</span>
      </div>

      <dl className="event-details-summary">
        <dt>Event ID</dt><dd>{event.id}</dd>
        <dt>Coordinates</dt><dd>{event.latitude.toFixed(5)}, {event.longitude.toFixed(5)}</dd>
        <dt>Started</dt><dd>{new Date(event.startedAt).toLocaleString()}</dd>
        <dt>Last observed</dt><dd>{new Date(event.lastObservedAt).toLocaleString()}</dd>
        <dt>Observations</dt><dd>{observations.length}</dd>
      </dl>

      <div className="event-observations-heading">
        <h3>Observations</h3>
        <span>{observations.length} report{observations.length === 1 ? '' : 's'}</span>
      </div>
      {observations.length === 0 ? <p>No observations linked to this event yet.</p> : (
        <ol className="event-details-observations">
          {observations.map((observation) => (
            <li key={observation.id}>
              <p className="observation-text">{observation.text}</p>
              <dl>
                <dt>Observed</dt><dd>{new Date(observation.observedAt).toLocaleString()}</dd>
                <dt>Coordinates</dt><dd>{observation.latitude.toFixed(5)}, {observation.longitude.toFixed(5)}</dd>
                <dt>Processing status</dt><dd>{observation.processingStatus}</dd>
                <dt>Observation ID</dt><dd>{observation.id}</dd>
              </dl>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
