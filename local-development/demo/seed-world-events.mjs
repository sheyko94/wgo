import { readFile, writeFile, mkdir } from 'node:fs/promises'

// Local development only. Submit through the normal observation pipeline.
const api = `${(process.env.API_PROXY_TARGET || 'http://localhost:8080').replace(/\/$/, '')}/v1`
const fixture = JSON.parse(await readFile(new URL('./world-observations.json', import.meta.url), 'utf8'))
const receiptsUrl = new URL('../data/world-demo-receipts.json', import.meta.url)
await mkdir(new URL('../data/', import.meta.url), { recursive: true })
let receipts = {}
try {
  receipts = JSON.parse(await readFile(receiptsUrl, 'utf8'))
} catch (error) {
  if (error.code !== 'ENOENT') throw error
}

async function getEvents() {
  const response = await fetch(`${api}/events`, { signal: AbortSignal.timeout(10000) })
  if (!response.ok) throw new Error(`GET events failed: ${response.status}`)
  return response.json()
}

const before = await getEvents()
const existingTexts = new Set(before.flatMap(event => event.observations.map(observation => observation.text)))
let submitted = 0
for (const observation of fixture) {
  if (existingTexts.has(observation.text) || receipts[observation.text]) continue
  const response = await fetch(`${api}/observations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(observation),
    signal: AbortSignal.timeout(10000),
  })
  if (!response.ok) throw new Error(`POST failed (${response.status}): ${observation.text}`)
  const accepted = await response.json()
  receipts[observation.text] = accepted.observationId
  await writeFile(receiptsUrl, `${JSON.stringify(receipts, null, 2)}\n`)
  submitted++
}
console.log(`Submitted ${submitted} observations; waiting for all 80 demo events to finish processing.`)

const texts = new Set(fixture.map(observation => observation.text))
const deadline = Date.now() + 180000
while (Date.now() < deadline) {
  const events = await getEvents()
  const demoEvents = events.filter(event => event.observations.some(observation => texts.has(observation.text)))
  const completed = new Set(demoEvents.flatMap(event => event.observations
    .filter(observation => texts.has(observation.text) && observation.processingStatus === 'PROCESSED')
    .map(observation => observation.text)))
  if (completed.size === fixture.length) {
    console.log(`Done: ${demoEvents.length} demo events, ${completed.size} processed demo observations, ${events.length} total events.`)
    if (demoEvents.length !== fixture.length) throw new Error('Some demo observations matched together; check local matching settings.')
    break
  }
  console.log(`Processed ${completed.size}/${fixture.length} demo observations.`)
  await new Promise(resolve => setTimeout(resolve, 5000))
}
if (Date.now() >= deadline) throw new Error('Processing did not finish within 3 minutes; check the consumer and SQS. Receipts are preserved for reruns.')
