export interface BlockDef {
  id: number;
  key: string;
  name: string;
  color: string;
  accent: string;
  top?: boolean;
  vertical?: boolean;
  ore?: boolean;
  light?: number;
  passable?: boolean;
}

export interface ItemDef {
  name: string;
  color: string;
  accent: string;
  weapon?: number;
}

export interface WeaponDef {
  tier: number;
  key: string;
  name: string;
  damage: number;
  range: number;
  cooldown: number;
}

export interface DropDef {
  item: string;
  min: number;
  max: number;
  chance: number;
}

export interface MobDef {
  key: string;
  name: string;
  hp: number;
  damage: number;
  speed: number;
  flying: boolean;
  aggro: number;
  w: number;
  h: number;
  weight: number;
  color: string;
  accent: string;
  drops: DropDef[];
}

export interface RecipeDef {
  id: string;
  name: string;
  in: Record<string, number>;
  out: Record<string, number>;
}

export interface BountyTemplate {
  title: string;
  wants: Record<string, number>;
  reward: Record<string, number>;
  minutes: number;
}

export interface Content {
  blocks: BlockDef[];
  items: Record<string, ItemDef>;
  weapons: WeaponDef[];
  recipes: RecipeDef[];
  mobs: MobDef[];
  bounties: { slots: number; templates: BountyTemplate[] };
  $simplifications?: string[];
}

export interface Bounty {
  id: string;
  title: string;
  wants: Record<string, number>;
  reward: Record<string, number>;
  expiresAt: number;
}

export interface BountiesView {
  bounties: Bounty[];
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
  kills: Record<string, number>;
  hp: number;
  weaponTier: number;
  weaponName: string;
  bounties: Bounty[];
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

export interface Mutation {
  ok: boolean;
  inventory: Record<string, number>;
  kills: Record<string, number>;
  weaponTier: number;
  weaponName: string;
  message: string;
  bounties: Bounty[] | null;
}

export interface Ack {
  ok: boolean;
  player: { x: number; y: number } | null;
  hp?: number;
}
