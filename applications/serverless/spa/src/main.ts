import { createApp } from 'vue';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
// Bootstrap JS loaded once (navbar menu on phones, modals, alerts, theme dropdown): twice breaks the collapse
import 'bootstrap';
import './styles/app.css';
import App from './App.vue';
import { router } from './router';

createApp(App).use(router).mount('#app');
