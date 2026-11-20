export interface BlockDef {
  id: number;
  key: string;
  name: string;
  hardness: number;
  tier: number;
  drop: string | null;
  color: string;
  accent: string;
  top?: boolean;
  vertical?: boolean;
  ore?: boolean;
  falls?: boolean;
  light?: number;
}

export interface ItemDef {
  name: string;
  color: string;
  accent: string;
  place?: number;
  tool?: number;
}

export interface ToolDef {
  tier: number;
  key: string;
  name: string;
  speed: number;
}

export interface RecipeDef {
  id: string;
  name: string;
  in: Record<string, number>;
  out: Record<string, number>;
  needsWorkbench: boolean;
}

export interface Content {
  blocks: BlockDef[];
  items: Record<string, ItemDef>;
  tools: ToolDef[];
  recipes: RecipeDef[];
  $simplifications?: string[];
}

export interface Order {
  id: string;
  title: string;
  wants: Record<string, number>;
  reward: Record<string, number>;
  expiresAt: number;
}

export interface OrdersView {
  orders: Order[];
  now: number;
}

export interface WorldView {
  id: string;
  name: string;
  seed: number;
  width: number;
  height: number;
  blocks: number[];
  surface: number[];
  player: { x: number; y: number };
  inventory: Record<string, number>;
  toolTier: number;
  toolName: string;
  reach: number;
  orders: Order[];
  now: number;
}

export interface WorldSummary {
  id: string;
  name: string;
  seed: number;
  width: number;
  height: number;
  updatedAt: string;
}

export interface BlockChange {
  x: number;
  y: number;
  id: number;
}

export interface Mutation {
  ok: boolean;
  changes: BlockChange[];
  inventory: Record<string, number>;
  toolTier: number;
  toolName: string;
  drop: string | null;
  message: string;
  player: { x: number; y: number } | null;
  orders: Order[] | null;
}
