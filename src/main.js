import { mount } from 'svelte'
import App from './App.svelte'

mount(App, { target: document.getElementById('app') })
navigator.storage?.persist?.() // ask the browser not to evict progress under storage pressure
if (import.meta.env.PROD && 'serviceWorker' in navigator) navigator.serviceWorker.register('sw.js')
