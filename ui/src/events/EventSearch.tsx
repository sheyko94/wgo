import { useEffect, useRef, useState } from 'react'

interface EventSearchProps {
  activeQuery: string
  onQuery: (query: string) => void
}

export function EventSearch({ activeQuery, onQuery }: EventSearchProps) {
  const [input, setInput] = useState(activeQuery)
  const wasFocused = useRef(false)

  useEffect(() => {
    if (wasFocused.current) {
      const timer = window.setTimeout(() => {
        document.getElementById('event-search-input')?.focus()
        wasFocused.current = false
      }, 0)
      return () => window.clearTimeout(timer)
    }
  }, [activeQuery])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const query = input.trim()
      if (query !== activeQuery) onQuery(query)
    }, 1000)
    return () => window.clearTimeout(timer)
  }, [input, activeQuery, onQuery])

  return (
    <div className="event-search">
      <label htmlFor="event-search-input">Search events</label>
      <div className="event-search-controls">
        <input
          id="event-search-input"
          type="search"
          value={input}
          onChange={(change) => {
            wasFocused.current = document.activeElement === change.currentTarget
            setInput(change.target.value)
          }}
          placeholder="e.g. travel disruptions or flooding"
        />
        {activeQuery && <button type="button" onClick={() => {
          setInput('')
          onQuery('')
        }}>Clear</button>}
      </div>
      {input.trim() !== activeQuery && <p className="search-status" role="status">Searching in 1 second…</p>}
      {activeQuery && input.trim() === activeQuery && <p className="search-status" role="status">Semantic results for “{activeQuery}”</p>}
    </div>
  )
}
