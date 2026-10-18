<script>
  import { onMount } from 'svelte'
  import * as sync from './lib/sync.js'
  import { LANG } from './lib/lang.js'

  /** Sign in with an emailed code, sync, sign out, delete the account. onsynced: reload after a sync brought changes. */
  let { onsynced } = $props()

  let user = $state(null)
  let email = $state('')
  let code = $state('')
  let sent = $state(false)
  let busy = $state(false)
  let note = $state('')
  let last = $state(null)

  onMount(async () => {
    user = await sync.account().catch(() => null)
    last = await sync.lastSynced()
  })

  async function step(fn) {
    busy = true
    note = ''
    try {
      await fn()
    } catch (e) {
      note = `⚠️ ${e.message}`
    }
    busy = false
  }

  const send = () => step(async () => {
    await sync.sendCode(email.trim())
    sent = true
    note = `Code sent to ${email.trim()}. It can take a minute; check spam too.`
  })

  const verify = () => step(async () => {
    user = await sync.verifyCode(email.trim(), code.trim())
    code = ''
    sent = false
    await now()
  })

  async function now() {
    const r = await sync.sync()
    last = await sync.lastSynced()
    note = r ? `✓ Synced: ${r.pulled} changes in, ${r.pushed} out.` : ''
    if (r?.pulled) onsynced?.()
  }

  const out = () => step(async () => {
    await sync.signOut()
    user = null
    note = 'Signed out. Your progress stays on this device.'
  })

  const remove = () => step(async () => {
    if (prompt('This deletes your account and everything saved with it on the server. Type DELETE to confirm.') !== 'DELETE') return
    const wipe = confirm('Also erase all progress on this device?')
    await sync.deleteAccount()
    user = null
    if (wipe) {
      indexedDB.deleteDatabase(`pluton-${LANG.code}`)
      location.reload()
    }
    note = 'Account deleted. This device keeps its progress.'
  })
</script>

<section class="panel">
  <h3>👤 Account</h3>
  {#if user}
    <p>Signed in as <b>{user.email}</b>. Your progress syncs across every device you sign in on.</p>
    <p class="small muted">{last ? `Last synced ${new Date(last).toLocaleString()}` : 'Not synced yet.'}</p>
    <div class="row">
      <button class="btn" onclick={() => step(now)} disabled={busy}>Sync now</button>
      <button class="btn ghost" onclick={out} disabled={busy}>Sign out</button>
    </div>
    <button class="link danger" onclick={remove} disabled={busy}>Delete my account</button>
  {:else}
    <p class="muted">Optional. Sign in to keep your progress in sync between your phone and your computer. No password:
      we email you a code. Your AI key never leaves the device.</p>
    {#if !sent}
      <label class="field-label">Email
        <input class="field" type="email" autocomplete="email" bind:value={email} placeholder="you@example.com" />
      </label>
      <div class="row"><button class="btn" onclick={send} disabled={busy || !email.includes('@')}>Email me a code</button></div>
    {:else}
      <label class="field-label">6-digit code
        <input class="field code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" bind:value={code} placeholder="123456" />
      </label>
      <div class="row">
        <button class="btn" onclick={verify} disabled={busy || code.trim().length < 6}>Sign in</button>
        <button class="btn ghost" onclick={() => (sent = false)} disabled={busy}>Use another email</button>
      </div>
    {/if}
  {/if}
  {#if note}<p class="note" class:bad={note.startsWith('⚠️')}>{note}</p>{/if}
</section>

<style>
  section { margin-bottom: 14px; }
  h3 { margin-bottom: 8px; }
  .field-label { display: block; font-weight: 700; font-size: 14px; margin: 12px 0 0; }
  .field { display: block; width: 100%; margin-top: 4px; padding: 11px 12px; border-radius: 12px; border: 2px solid var(--line); background: var(--panel); font-size: 16px; font-weight: 400; }
  .field:focus { outline: none; border-color: var(--blue); }
  .code { letter-spacing: 0.3em; font-size: 22px; font-weight: 700; }
  .row { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 12px; }
  .small { font-size: 13px; }
  .note { margin: 10px 0 0; font-weight: 600; color: var(--green); }
  .note.bad { color: var(--red); }
  .link { border: 0; background: none; padding: 0; margin-top: 14px; color: var(--muted); text-decoration: underline; cursor: pointer; font-size: 14px; }
  .danger { color: var(--red); }
</style>
