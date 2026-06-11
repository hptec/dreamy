import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { vDismiss } from './directives/dismiss'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.directive('dismiss', vDismiss)
app.mount('#app')
