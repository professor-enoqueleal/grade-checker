import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { setUnauthorizedHandler } from '@/api/client';
import { useSession } from '@/composables/useSession';
import { applyTheme, useTheme } from '@/composables/useTheme';

/**
 * Same URLs as the JSP version (bookmarks and E2E tests keep working). CloudFront (a CloudFront Function) and the Vite
 * dev server answer index.html for these paths; /api/* goes to the API Gateway.
 *
 * meta.public: no session needed. meta.nav: link of the navbar marked as current.
 */
declare module 'vue-router' {
  interface RouteMeta {
    title: string;
    public?: boolean;
    nav?: 'groups' | 'grades';
  }
}

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: 'Sign in · Grade Checker', public: true } },
  { path: '/authentication.jsp', redirect: '/login' },
  { path: '/home', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { title: 'Grade Checker' } },
  { path: '/detail', name: 'group', component: () => import('@/views/GroupDetailView.vue'), meta: { title: 'Grade Checker | Group Detail' } },
  { path: '/contributions-detail', name: 'commits', component: () => import('@/views/CommitsView.vue'), meta: { title: 'Grade Checker | Commits Detail' } },
  { path: '/grades', name: 'grades', component: () => import('@/views/GradesView.vue'), meta: { title: 'Grade Checker | Class grades', nav: 'grades' } },
  { path: '/groups', name: 'groups', component: () => import('@/views/GroupsView.vue'), meta: { title: 'Grade Checker | Manage groups', nav: 'groups' } },
  { path: '/group/members', name: 'members', component: () => import('@/views/GroupMembersView.vue'), meta: { title: 'Grade Checker | Manage members' } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { title: 'Grade Checker | Page not found' } },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
});

const { load, clear } = useSession();

// route protection (openspec capability route-protection): no session -> sign-in page; signed in -> no sign-in page
router.beforeEach(async (to) => {
  let me = null;
  try {
    me = await load();
  } catch {
    me = null;   // backend unreachable: the protected pages show their own errors after signing in again
  }
  // as in the Jetty version, the sign-in page says why it opened
  if (!to.meta.public && !me) return { name: 'login', query: { signIn: 'required' } };
  if (to.name === 'login' && me) return { name: 'home' };
  return true;
});

router.afterEach((to) => {
  document.title = to.meta.title;
  // the theme toggle exists only on the sign-in page; the other pages stay light, as before
  applyTheme(to.name === 'login' ? useTheme().theme.value : null);
});

// a call answered 401 (session expired): back to the sign-in page
setUnauthorizedHandler(() => {
  clear();
  if (router.currentRoute.value.name !== 'login') router.push({ name: 'login', query: { signIn: 'required' } });
});
