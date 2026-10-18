// Bring-your-own-key AI. The key is stored on this device only and sent only to the chosen provider:
// there is no Pluton server. Claude goes through Anthropic's SDK; everything else speaks the OpenAI chat format.
// The SDK is ~90 KB gzipped: loaded on the first Claude request, not with the app.
let SDK = null
const sdk = async () => (SDK ??= (await import('@anthropic-ai/sdk')).default)

export const PROVIDERS = {
  anthropic: { name: 'Claude (Anthropic)', base: 'https://api.anthropic.com', keys: 'https://console.anthropic.com/settings/keys',
    model: 'claude-opus-5-5' },
  openai: { name: 'ChatGPT models (OpenAI)', base: 'https://api.openai.com/v1', keys: 'https://platform.openai.com/api-keys' },
  deepseek: { name: 'DeepSeek', base: 'https://api.deepseek.com', keys: 'https://platform.deepseek.com/api_keys' },
  openrouter: { name: 'OpenRouter (hundreds of models, one key)', base: 'https://openrouter.ai/api/v1', keys: 'https://openrouter.ai/keys' },
  ollama: { name: 'Ollama (local, free)', base: 'http://localhost:11434/v1', noKey: true },
  custom: { name: 'Other OpenAI-compatible server', base: '' },
}

// Claude models that take output_config.effort, and those that take server-side refusal fallbacks.
const EFFORT = /^claude-(fable|mythos|opus|sonnet|haiku)-[5-9]|^claude-opus-4-[5-8]|^claude-sonnet-4-6/
const FALLBACKS = new Set(['claude-fable-5-1', 'claude-opus-5-5', 'claude-opus-5', 'claude-sonnet-5-5'])

export class AIError extends Error {}

// The active config, set by the app, for features that use AI opportunistically (e.g. translating mined sentences).
let active = null
export const setActive = (cfg) => (active = cfg)
export const activeConfig = () => (ready(active) ? active : null)

const baseOf = (cfg) => (cfg.base || PROVIDERS[cfg.provider]?.base || '').replace(/\/+$/, '')
export const ready = (cfg) => !!(cfg?.provider && cfg.model && (cfg.key || PROVIDERS[cfg.provider]?.noKey || cfg.provider === 'custom'))

async function anthropic(cfg) {
  const Anthropic = await sdk()
  return new Anthropic({ apiKey: cfg.key, baseURL: baseOf(cfg), dangerouslyAllowBrowser: true, maxRetries: 1 })
}

function explain(err, cfg) {
  const is = (name) => SDK && err instanceof SDK[name] // SDK error classes, when the SDK was used
  if (err instanceof AIError || err?.name === 'AbortError' || is('APIUserAbortError')) return err
  const status = err?.status
  if (is('AuthenticationError') || status === 401) return new AIError('The provider rejected the API key.')
  if (is('PermissionDeniedError') || status === 403) return new AIError('This key is not allowed to use that model.')
  if (is('NotFoundError') || status === 404) return new AIError(`Model "${cfg.model}" was not found. Pick another in Settings.`)
  if (is('RateLimitError') || status === 429) return new AIError('Rate limited or out of credit. Try again in a minute, or check your balance.')
  if (is('APIConnectionError') || err instanceof TypeError)
    return new AIError(cfg.provider === 'ollama'
      ? "Couldn't reach Ollama. Is it running? From a website you also need OLLAMA_ORIGINS set (see Settings)."
      : "Couldn't reach the provider. Are you offline?")
  return new AIError(err?.message || 'The AI request failed.')
}

/**
 * Streams a reply. `messages` is [{role: 'user'|'assistant', content: string}]. onText gets each text delta.
 * Resolves to the full text. effort: how hard Claude thinks (low suits chat, medium suits corrections).
 */
export async function chat(cfg, { system, messages, effort = 'low', onText, signal }) {
  try {
    if (cfg.provider === 'anthropic') {
      const stream = (await anthropic(cfg)).beta.messages.stream({
        model: cfg.model, max_tokens: 16000, system, messages,
        ...(EFFORT.test(cfg.model) && { output_config: { effort } }),
        ...(FALLBACKS.has(cfg.model) && { betas: ['server-side-fallback-2026-07-01'], fallbacks: 'default' }),
      }, { signal })
      if (onText) stream.on('text', onText)
      const msg = await stream.finalMessage()
      if (msg.stop_reason === 'refusal') throw new AIError('The model declined to answer that one.')
      return msg.content.filter((b) => b.type === 'text').map((b) => b.text).join('')
    }
    const res = await fetch(`${baseOf(cfg)}/chat/completions`, {
      method: 'POST', signal,
      headers: { 'Content-Type': 'application/json', ...(cfg.key && { Authorization: `Bearer ${cfg.key}` }) },
      body: JSON.stringify({ model: cfg.model, stream: true, messages: [{ role: 'system', content: system }, ...messages] }),
    })
    if (!res.ok) throw Object.assign(new Error(await res.text().catch(() => res.statusText)), { status: res.status })
    let text = '', buf = ''
    for await (const chunk of res.body.pipeThrough(new TextDecoderStream())) {
      buf += chunk
      const lines = buf.split('\n')
      buf = lines.pop()
      for (const line of lines) {
        const data = line.replace(/^data:\s*/, '').trim()
        if (!line.startsWith('data:') || !data || data === '[DONE]') continue
        const delta = JSON.parse(data).choices?.[0]?.delta?.content
        if (delta) {
          text += delta
          onText?.(delta)
        }
      }
    }
    return text
  } catch (err) {
    throw explain(err, cfg)
  }
}

/** Model ids the key can use, for the Settings picker. */
export async function listModels(cfg) {
  try {
    if (cfg.provider === 'anthropic') {
      const ids = []
      for await (const m of (await anthropic(cfg)).models.list()) ids.push(m.id)
      return ids
    }
    const res = await fetch(`${baseOf(cfg)}/models`, { headers: cfg.key ? { Authorization: `Bearer ${cfg.key}` } : {} })
    if (!res.ok) throw Object.assign(new Error(res.statusText), { status: res.status })
    return (await res.json()).data.map((m) => m.id).sort()
  } catch (err) {
    throw explain(err, cfg)
  }
}

/** The first {...} object in a reply, for prompts that ask for JSON (works across providers). */
export function parseJSON(text) {
  const start = text.indexOf('{'), end = text.lastIndexOf('}')
  if (start < 0 || end < start) throw new AIError('The model did not return the expected format. Try again.')
  try {
    return JSON.parse(text.slice(start, end + 1))
  } catch {
    throw new AIError('The model returned malformed JSON. Try again.')
  }
}
