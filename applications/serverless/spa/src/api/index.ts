import { request } from './client';
import type {
  AppConfig, ArchivedGroupsResponse, CommitHistory, ContributionsReport, GradebookResponse, GroupDetail,
  GroupGrades, GroupMembersResponse, GroupSummary, Me, Member,
} from './types';

/** Period of the dashboard and of the commit history: m1/m2/m3/all, or custom with since/until (yyyy-MM-ddTHH:mm). */
export interface PeriodQuery {
  period: string;
  since?: string;
  until?: string;
}

/** Every /api call of the SPA (contract in README.md). */
export const api = {
  // ------------------------------------------------------------ session (public)
  me: () => request<Me>('GET', '/me', { allow401: true }),
  signInWithToken: (token: string) => request<Me>('POST', '/auth/token', { body: { token }, allow401: true }),
  signOut: () => request<void>('POST', '/auth/logout', { allow401: true }),
  /** full page navigation: the backend answers 302 to GitHub */
  gitHubSignInUrl: '/api/auth/github',

  // ------------------------------------------------------------ authenticated
  config: () => request<AppConfig>('GET', '/config'),
  groups: () => request<GroupSummary[]>('GET', '/groups'),
  group: (id: number) => request<GroupDetail>('GET', `/groups/${id}`),
  contributions: (id: number, p: PeriodQuery) =>
    request<ContributionsReport>('GET', `/groups/${id}/contributions`, { query: { ...p } }),
  commits: (id: number, author: string, p: PeriodQuery) =>
    request<CommitHistory>('GET', `/groups/${id}/commits`, { query: { author, ...p } }),

  // ------------------------------------------------------------ administrators
  archivedGroups: () => request<ArchivedGroupsResponse>('GET', '/groups/archived'),
  createGroup: (name: string, repositoryUrl: string) =>
    request<GroupSummary>('POST', '/groups', { body: { name, repositoryUrl } }),
  updateGroup: (id: number, name: string, repositoryUrl: string) =>
    request<GroupSummary>('PUT', `/groups/${id}`, { body: { name, repositoryUrl } }),
  archiveGroup: (id: number) => request<GroupSummary>('POST', `/groups/${id}/archive`),
  restoreGroup: (id: number) => request<GroupSummary>('POST', `/groups/${id}/restore`),

  members: (groupId: number) => request<GroupMembersResponse>('GET', `/groups/${groupId}/members`),
  /** manual = true from "Manage members" (name typed); false from "Mark" on a card (name = login) */
  addMember: (groupId: number, login: string, name: string | null, manual: boolean) =>
    request<Member>('POST', `/groups/${groupId}/members`, { body: { login, name, manual } }),
  updateMember: (groupId: number, login: string, newLogin: string, name: string) =>
    request<Member>('PUT', `/groups/${groupId}/members/${encodeURIComponent(login)}`, { body: { login: newLogin, name } }),
  removeMember: (groupId: number, login: string) =>
    request<void>('DELETE', `/groups/${groupId}/members/${encodeURIComponent(login)}`),

  linkIdentity: (groupId: number, identityKey: string, login: string) =>
    request<void>('POST', `/groups/${groupId}/aliases`, { body: { identityKey, login } }),
  unlinkIdentity: (groupId: number, identityKey: string) =>
    request<void>('DELETE', `/groups/${groupId}/aliases`, { query: { identityKey } }),

  gradebook: () => request<GradebookResponse>('GET', '/grades'),
  groupGrades: (groupId: number) => request<GroupGrades>('GET', `/grades/${groupId}`),
};
