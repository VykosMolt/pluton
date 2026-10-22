import { mount } from 'svelte'
import { Capacitor } from '@capacitor/core'
import { Browser } from '@capacitor/browser'
import App from './App.svelte'

mount(App, { target: document.getElementById('app') })
navigator.storage?.persist?.() // ask the browser not to evict progress under storage pressure
// native builds ship their assets in the app; a service worker adds nothing and can serve stale files after an update
if (import.meta.env.PROD && !Capacitor.isNativePlatform() && 'serviceWorker' in navigator) navigator.serviceWorker.register('sw.js')
// in a native app, an external link must not replace Pluton with a web page that has no way back
if (Capacitor.isNativePlatform()) {
  document.addEventListener('click', (e) => {
    const a = e.target.closest?.('a[href^="http"]')
    if (a) { e.preventDefault(); Browser.open({ url: a.href }) }
  })
}
