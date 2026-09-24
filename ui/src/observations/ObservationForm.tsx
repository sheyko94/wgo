import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { WorldEvent } from '../events/events'
import './ObservationForm.css'

export interface ReportLocation { latitude: number; longitude: number }
export interface ReportDraft { location: ReportLocation; event?: WorldEvent }

function localNow() {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

export function ObservationForm({ draft, onCancel, onSubmitted }: {
  draft: ReportDraft
  onCancel: () => void
  onSubmitted: (id: string) => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const submitting = useRef(false)
  const [description, setDescription] = useState('')
  const [observedAt, setObservedAt] = useState(localNow)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) return
    if (!description.trim() || !Number.isFinite(Date.parse(observedAt))) {
      setError('Enter a description and valid observation time.')
      return
    }
    submitting.current = true
    setBusy(true)
    setError(null)
    try {
      const endpoint = draft.event
        ? `/v1/events/${encodeURIComponent(draft.event.id)}/observations`
        : '/v1/observations'
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({
          text: description.trim(),
          location: draft.location,
          observedAt: new Date(observedAt).toISOString(),
        }),
      })
      if (!response.ok) {
        if (response.status === 404) throw new Error('This event no longer exists. Close this form and refresh the map.')
        if (response.status === 400) throw new Error('Check the report text, coordinates, and observation time.')
        throw new Error('Submission could not be confirmed. The report may have been saved; refresh events before trying again.')
      }
      const result: unknown = await response.json()
      if (!result || typeof result !== 'object' || !('observationId' in result) || typeof result.observationId !== 'string') {
        throw new Error('The report was accepted, but its ID could not be read. Refresh events before submitting again.')
      }
      onSubmitted(result.observationId)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Submission could not be confirmed. Refresh events before trying again.')
    } finally {
      submitting.current = false
      setBusy(false)
    }
  }

  return (
    <dialog className="observation-dialog" ref={dialog} aria-labelledby="report-heading" onCancel={(event) => {
      event.preventDefault()
      if (!submitting.current) onCancel()
    }}>
      <form onSubmit={submit}>
        <h2 id="report-heading">{draft.event ? 'Add observation' : 'Report observation'}</h2>
        {draft.event && <p>For: <strong>{draft.event.title}</strong></p>}
        <p>Location: {draft.location.latitude.toFixed(5)}, {draft.location.longitude.toFixed(5)}</p>
        <label htmlFor="report-description">Description</label>
        <textarea id="report-description" autoFocus required maxLength={5200} rows={5} value={description} disabled={busy}
          placeholder="Describe what you observed and any uncertainty." onChange={(event) => setDescription(event.target.value)} />
        <label htmlFor="report-time">Observed at (your local time)</label>
        <input id="report-time" type="datetime-local" required value={observedAt} disabled={busy}
          onChange={(event) => setObservedAt(event.target.value)} />
        <p className="report-hint">{draft.event
          ? 'Your observation will be added to this event after processing.'
          : 'Your report may create a new event or join a related event after processing.'}</p>
        {error && <p role="alert" className="events-error">{error}</p>}
        <div className="report-actions">
          <button type="button" onClick={onCancel} disabled={busy}>Cancel</button>
          <button type="submit" disabled={busy}>{busy ? 'Submitting…' : 'Submit observation'}</button>
        </div>
      </form>
    </dialog>
  )
}
