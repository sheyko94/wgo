import { useEffect, useRef, useState } from 'react'
import type { WorldEvent } from './events'
import { categoryOf, eventCategories, summarizeEvent } from './events'

interface EventDetailsProps {
  event: WorldEvent
  onBack: () => void
  onEventUpdated: (event: WorldEvent) => void
}

export function EventDetails({ event, onBack, onEventUpdated }: EventDetailsProps) {
  const summary = event.generatedSummary ?? null
  const [summarizing, setSummarizing] = useState(false)
  const [summaryError, setSummaryError] = useState<string | null>(null)
  const request = useRef<AbortController | null>(null)
  useEffect(() => () => request.current?.abort(), [])

  async function handleSummarize() {
    if (request.current) return
    const controller = new AbortController()
    request.current = controller
    setSummarizing(true)
    setSummaryError(null)
    try {
      const result = await summarizeEvent(event.id, controller.signal)
      if (!controller.signal.aborted) onEventUpdated(result)
    } catch (error) {
      if (!controller.signal.aborted) setSummaryError(error instanceof Error ? error.message : 'Could not generate a summary.')
    } finally {
      if (!controller.signal.aborted) setSummarizing(false)
      request.current = null
    }
  }

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
        <dt>Status</dt><dd>{event.status === 'INACTIVE' ? 'Inactive' : event.status === 'ACTIVE' ? 'Active' : 'Unknown'}</dd>
        <dt>Event ID</dt><dd>{event.id}</dd>
        <dt>Coordinates</dt><dd>{event.latitude.toFixed(5)}, {event.longitude.toFixed(5)}</dd>
        <dt>Started</dt><dd>{new Date(event.startedAt).toLocaleString()}</dd>
        <dt>Last observed</dt><dd>{new Date(event.lastObservedAt).toLocaleString()}</dd>
        <dt>Observations</dt><dd>{observations.length}</dd>
      </dl>

      <section className="event-ai-summary" aria-labelledby="ai-summary-heading" aria-busy={summarizing}>
        <h3 id="ai-summary-heading">AI title and summary</h3>
        <p>Generated from reports with Claude. Reports are not independently verified.</p>
        <button type="button" onClick={handleSummarize} disabled={summarizing || observations.length === 0}>
          {summarizing ? 'Summarizing…' : summary ? 'Update summary' : 'Summarize event'}
        </button>
        {summarizing && <p role="status">Checking for an updated title and summary…</p>}
        {summaryError && <p className="events-error" role="alert">{summaryError}</p>}
        {summary && <div aria-live="polite">
          <h4>{summary.title}</h4>
          <p className="generated-summary-text">{summary.summary}</p>
          <p>Supporting observations:</p>
          <ul>{summary.observationIds.map((id) => {
            const index = observations.findIndex((observation) => observation.id === id)
            return <li key={id}>{index >= 0
              ? <a href={`#observation-${id}`}>Report {index + 1}</a>
              : <span>Report {id} (refresh to load)</span>}</li>
          })}</ul>
          <small>Saved {new Date(summary.generatedAt).toLocaleString()}. Active events refresh when new reports arrive; unchanged reports reuse the saved summary.</small>
          {event.summaryStale && <p role="status">New reports are awaiting a summary update. Inactive events keep their last saved summary.</p>}
        </div>}
      </section>

      <div className="event-observations-heading">
        <h3>Observations</h3>
        <span>{observations.length} report{observations.length === 1 ? '' : 's'}</span>
      </div>
      {observations.length === 0 ? <p>No observations linked to this event yet.</p> : (
        <ol className="event-details-observations">
          {observations.map((observation) => (
            <li key={observation.id} id={`observation-${observation.id}`}>
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
