import './App.css'
import { EventExplorer } from './events/EventExplorer'

function App() {
  return (
    <div className="app">
      <header className="app-header">
        <span className="wordmark">WGO</span>
        <span>World observations & events</span>
      </header>
      <main>
        <h1>Explore what’s happening</h1>
        <p>Discover observations and the events they connect to.</p>
        <EventExplorer />
      </main>
    </div>
  )
}

export default App
