import { describe, expect, it, vi } from 'vitest';
import { mount, RouterLinkStub } from '@vue/test-utils';

const session = { me: null as null | { login: string; isAdmin: boolean; avatarUrl?: string } };
vi.mock('@/composables/useSession', () => ({ useSession: () => ({ session, clear: vi.fn() }) }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import AppNavbar from '@/components/AppNavbar.vue';

function render(active?: 'groups' | 'grades') {
  return mount(AppNavbar, { props: { active }, global: { stubs: { RouterLink: RouterLinkStub } } });
}

describe('navbar (openspec capability serverless-navigation-bar)', () => {
  it('administrator: Manage groups and Class grades, the current one marked', () => {
    session.me = { login: 'professor', isAdmin: true };
    const w = render('grades');
    expect(w.find('#manage-groups-link').exists()).toBe(true);
    expect(w.find('#class-grades-link').attributes('aria-current')).toBe('page');
    expect(w.find('#manage-groups-link').attributes('aria-current')).toBeUndefined();
    expect(w.find('#navbar-user').text()).toBe('professor');
    expect(w.find('#sign-out-link').text()).toBe('Sign out');
  });

  it('user menu: avatar and login in the button; signed in as, role and Sign out inside the menu', () => {
    session.me = { login: 'professor', isAdmin: true, avatarUrl: 'https://github.com/professor.png?size=64' };
    let w = render();
    const toggle = w.find('#user-menu');
    expect(toggle.attributes('data-bs-toggle')).toBe('dropdown');
    expect(toggle.find('#navbar-avatar').attributes('src')).toBe('https://github.com/professor.png?size=64');
    expect(toggle.find('#navbar-user').text()).toBe('professor');
    const menu = w.find('#user-menu-items');
    expect(menu.classes()).toContain('dropdown-menu-end');
    expect(menu.find('#user-menu-signed-in').text()).toBe('Signed in as professor');
    expect(menu.find('#user-menu-role').text()).toBe('Administrator');
    expect(menu.find('#sign-out-link').classes()).toContain('dropdown-item');

    session.me = { login: 'ana', isAdmin: false };
    w = render();
    expect(w.find('#navbar-avatar').exists()).toBe(false);
    expect(w.find('#user-menu-role').text()).toBe('Student');
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
