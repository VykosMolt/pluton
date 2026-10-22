// A fake AI provider for the smoke test: OpenAI-style chat completions under /openai/v1 and the Anthropic Messages
// API under /anthropic/v1, both streaming, with browser CORS. Records requests so the test can check what was sent.
import { createServer } from 'node:http'

const CHAT = "✏️ Je suis allée au marché. — with être the participle agrees: allée\nSuper ! Qu'est-ce que tu as acheté au marché ?"
const WRITING = JSON.stringify({
  corrected: "Je m'appelle Léa. J'habite à Paris et j'aime lire.",
  errors: [{ original: 'a Paris', fix: 'à Paris', why: 'The preposition à needs its accent.', kind: 'spelling' }],
  feedback: 'Clear and natural. Watch your accents.',
  level: 'A1',
})
const STORY = JSON.stringify({
  title: 'Le marché de minuit',
  body: "La nuit, le marché de Paris est vide. Pluton marche entre les étals fermés. Une petite lumière brille au fond de la rue. C'est une boulangerie. La boulangère donne un pain chaud au chat noir. « Tu as faim, toi ? » demande-t-elle. Pluton mange le pain, puis il monte sur le toit. En bas, un chien maigre le regarde. « Tu viens ? » demande Pluton. Le chien monte. Ensemble, ils regardent la ville. Demain, le marché sera plein de monde, mais cette nuit, Paris appartient aux chats et aux chiens.",
})
const RETELL_REPLY = JSON.stringify({ score: 4, feedback: 'You got the main idea: Pluton explores the empty market and shares bread. You missed that he is joined by a thin dog on the roof.' })
const reply = (system) => system.includes('short French stories') ? STORY
  : system.includes('whether a French learner understood') ? RETELL_REPLY
  : system.includes('JSON object') ? WRITING
  : system.includes('Translate') ? 'I went to the market.' : CHAT
const chunks = (s) => s.match(/[\s\S]{1,12}/g)

export function startMockAI(port) {
  const requests = []
  const server = createServer(async (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*')
    res.setHeader('Access-Control-Allow-Headers', '*')
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
    res.setHeader('Content-Type', 'application/json') // SSE responses override it; the SDK needs it to parse JSON
    if (req.method === 'OPTIONS') return res.end()
    let body = ''
    for await (const c of req) body += c
    const json = body ? JSON.parse(body) : null
    requests.push({ path: req.url, headers: req.headers, body: json })
    const sse = () => res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' })
    const send = (event, data) => res.write(`${event ? `event: ${event}\n` : ''}data: ${JSON.stringify(data)}\n\n`)

    if (req.url === '/openai/v1/models') return res.end(JSON.stringify({ data: [{ id: 'mock-tutor' }, { id: 'mock-big' }] }))
    if (req.url === '/openai/v1/chat/completions') {
      sse()
      for (const c of chunks(reply(json.messages[0].content))) send(null, { choices: [{ delta: { content: c } }] })
      res.write('data: [DONE]\n\n')
      return res.end()
    }
    if (req.url.startsWith('/anthropic/v1/models')) {
      const data = ['claude-opus-5-5', 'claude-haiku-5-5'].map((id) => ({ type: 'model', id, display_name: id, created_at: '2026-01-01T00:00:00Z' }))
      return res.end(JSON.stringify({ data, has_more: false, first_id: data[0].id, last_id: data[1].id }))
    }
    if (req.url.startsWith('/anthropic/v1/messages')) {
      if (req.headers['x-api-key'] !== 'sk-test') return res.writeHead(401).end(JSON.stringify({ type: 'error', error: { type: 'authentication_error', message: 'bad key' } }))
      sse()
      const usage = { input_tokens: 10, output_tokens: 1 }
      send('message_start', { type: 'message_start', message: { id: 'msg_mock', type: 'message', role: 'assistant', model: json.model, content: [], stop_reason: null, stop_sequence: null, usage } })
      send('content_block_start', { type: 'content_block_start', index: 0, content_block: { type: 'text', text: '' } })
      for (const c of chunks(reply(json.system ?? ''))) send('content_block_delta', { type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: c } })
      send('content_block_stop', { type: 'content_block_stop', index: 0 })
      send('message_delta', { type: 'message_delta', delta: { stop_reason: 'end_turn', stop_sequence: null }, usage: { output_tokens: 20 } })
      send('message_stop', { type: 'message_stop' })
      return res.end()
    }
    res.writeHead(404).end()
  })
  return new Promise((ok) => server.listen(port, () => ok({ requests, close: () => server.close() })))
}
