import type { Ack, BountiesView, Content, Mutation, WorldSummary, WorldView } from './types';

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
  remove: (id: string) => req<Ack>(`/worlds/${id}`, 'DELETE'),
  // 客户端判定挥砍命中，服务端只结算「这只怪死了」：掉落表与击杀计数都在后端。
  loot: (id: string, mob: string) => req<Mutation>(`/worlds/${id}/loot`, 'POST', { mob }),
  craft: (id: string, recipeId: string) => req<Mutation>(`/worlds/${id}/craft`, 'POST', { recipeId }),
  bounties: (id: string) => req<BountiesView>(`/worlds/${id}/bounties`),
  deliver: (id: string, bountyId: string) => req<Mutation>(`/worlds/${id}/deliver`, 'POST', { bountyId }),
  move: (id: string, x: number, y: number, hp?: number) =>
    req<Ack>(`/worlds/${id}/move`, 'POST', { x, y, hp }),
};
