import { createApp } from 'vue';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
// Bootstrap JS loaded once (navbar menu on phones, modals, alerts, theme dropdown): twice breaks the collapse
import 'bootstrap';
import './styles/app.css';
import App from './App.vue';
import { i18n } from './i18n';
import { applyLocale } from './composables/useLocale';
import { router } from './router';

applyLocale(i18n.global.locale.value);
createApp(App).use(i18n).use(router).mount('#app');
