import type { Content, Mutation, WorldSummary, WorldView } from './types';

export class ApiError extends Error {
  constructor(public code: string, message: string) {
    super(message);
  }
}

async function req<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await fetch(`/api${path}`, {
    method,
    headers: body === undefined ? undefined : { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(data?.code ?? `HTTP_${res.status}`, data?.message ?? '服务器没有回应');
  return data as T;
}

export const api = {
  content: () => req<Content>('/content'),
  worlds: () => req<WorldSummary[]>('/worlds'),
  create: (name: string, seed?: number) => req<WorldView>('/worlds', 'POST', { name, seed }),
  load: (id: string) => req<WorldView>(`/worlds/${id}`),
  remove: (id: string) => req<{ ok: boolean }>(`/worlds/${id}`, 'DELETE'),
  mine: (id: string, x: number, y: number) => req<Mutation>(`/worlds/${id}/mine`, 'POST', { x, y }),
  place: (id: string, x: number, y: number, item: string) =>
    req<Mutation>(`/worlds/${id}/place`, 'POST', { x, y, item }),
  craft: (id: string, recipeId: string) => req<Mutation>(`/worlds/${id}/craft`, 'POST', { recipeId }),
  move: (id: string, x: number, y: number) => req<{ ok: boolean }>(`/worlds/${id}/move`, 'POST', { x, y }),
};
