import { apiErrorText } from '@/i18n/apiErrors';
import type { ApiErrorBody } from './types';

/** Error of an /api call: HTTP status plus the message of the backend ({ error, message }). */
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
type Query = Record<string, string | number | boolean | null | undefined>;

/** Called when a protected call answers 401 (session expired or missing); set by the router. */
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

function url(path: string, query?: Query): string {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') params.set(key, String(value));
  });
  const qs = params.toString();
  return '/api' + path + (qs ? '?' + qs : '');
}

/**
 * Same origin (CloudFront or the Vite proxy): the session cookie (HttpOnly) goes with every call.
 * Every call carries "X-Requested-With: XMLHttpRequest", which a cross-site form cannot set (CSRF protection).
 */
export async function request<T>(method: Method, path: string, options: { query?: Query; body?: unknown; allow401?: boolean } = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url(path, options.query), {
      method,
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json',
        'X-Requested-With': 'XMLHttpRequest',
        ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'network', 'Could not reach the server. Please try again.');
  }

  const text = await response.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
  }

  if (!response.ok) {
    const error = body as Partial<ApiErrorBody> | null;
    if (response.status === 401 && !options.allow401 && onUnauthorized) onUnauthorized();
    throw new ApiError(response.status, error?.error ?? 'error', error?.message ?? `Request failed (HTTP ${response.status}).`);
  }
  return body as T;
}

/** Message of an error for the screen, in the user's language, or the fallback when the backend sent none. */
export function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError && error.message ? apiErrorText(error.code, error.message) : fallback;
}
