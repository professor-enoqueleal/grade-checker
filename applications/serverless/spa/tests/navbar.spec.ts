import { describe, expect, it, vi } from 'vitest';
import { mount, RouterLinkStub } from '@vue/test-utils';

const session = { me: null as null | { login: string; isAdmin: boolean } };
vi.mock('@/composables/useSession', () => ({ useSession: () => ({ session, clear: vi.fn() }) }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import AppNavbar from '@/components/AppNavbar.vue';

function render(active?: 'groups' | 'grades') {
  return mount(AppNavbar, { props: { active }, global: { stubs: { RouterLink: RouterLinkStub } } });
}

describe('navbar (openspec capability navigation-bar)', () => {
  it('administrator: Manage groups and Class grades, the current one marked', () => {
    session.me = { login: 'professor', isAdmin: true };
    const w = render('grades');
    expect(w.find('#manage-groups-link').exists()).toBe(true);
    expect(w.find('#class-grades-link').attributes('aria-current')).toBe('page');
    expect(w.find('#manage-groups-link').attributes('aria-current')).toBeUndefined();
    expect(w.find('#navbar-user').text()).toBe('professor');
    expect(w.find('#sign-out-link').text()).toBe('Sign out');
  });

  it('student: no administrator links, no repeated ids', () => {
    session.me = { login: 'ana', isAdmin: false };
    const w = render();
    expect(w.find('#manage-groups-link').exists()).toBe(false);
    expect(w.find('#class-grades-link').exists()).toBe(false);
    const ids = w.findAll('[id]').map((e) => e.attributes('id'));
    expect(new Set(ids).size).toBe(ids.length);
  });
});
