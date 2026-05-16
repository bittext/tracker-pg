import type { NowRoadmapLane } from './management-now-data';
import {
  NOW_ROADMAP_ACTIVE,
  NOW_ROADMAP_DONE,
  NOW_ROADMAP_PLANNED,
  NowRoadmapCard,
  NowRoadmapCardType,
  NOW_ROADMAP_CARD_TYPES,
  nowRoadmapDefaultLane,
  nowRoadmapStaticCardById,
} from './management-now-data';

const STORAGE_KEY = 'tracker-pg.management-now-board.v1';

export interface NowBoardLanes {
  readonly planned: string[];
  readonly active: string[];
  readonly done: string[];
}

interface PersistedBlob {
  planned: string[];
  active: string[];
  done: string[];
  customCards?: Record<string, unknown>;
}

export function nowBoardDefaultLanes(): NowBoardLanes {
  return {
    planned: NOW_ROADMAP_PLANNED.map((c) => c.id),
    active: NOW_ROADMAP_ACTIVE.map((c) => c.id),
    done: NOW_ROADMAP_DONE.map((c) => c.id),
  };
}

function isLaneArray(v: unknown): v is string[] {
  return Array.isArray(v) && v.every((x) => typeof x === 'string');
}

function isCardType(v: unknown): v is NowRoadmapCardType {
  return typeof v === 'string' && (NOW_ROADMAP_CARD_TYPES as readonly string[]).includes(v);
}

function parseOneCustomCard(id: string, raw: unknown): NowRoadmapCard | null {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const o = raw as Record<string, unknown>;
  const title = o['title'];
  const type = o['type'];
  if (typeof title !== 'string' || !title.trim() || !isCardType(type)) {
    return null;
  }
  const body = o['body'];
  const milestone = o['milestone'];
  return {
    id,
    type,
    title: title.trim(),
    ...(typeof body === 'string' && body.trim() ? { body: body.trim() } : {}),
    ...(typeof milestone === 'string' && milestone.trim() ? { milestone: milestone.trim() } : {}),
  };
}

function parseCustomCards(raw: unknown): Map<string, NowRoadmapCard> {
  const m = new Map<string, NowRoadmapCard>();
  if (!raw || typeof raw !== 'object') {
    return m;
  }
  for (const [id, v] of Object.entries(raw as Record<string, unknown>)) {
    if (!id.startsWith('now-custom-')) {
      continue;
    }
    const c = parseOneCustomCard(id, v);
    if (c) {
      m.set(id, c);
    }
  }
  return m;
}

/** Static catalog plus any cards saved in this browser. */
export function nowBoardFullCatalog(): Map<string, NowRoadmapCard> {
  const merged = nowRoadmapStaticCardById();
  const blob = readPersistedBlob();
  for (const [id, c] of parseCustomCards(blob?.customCards)) {
    merged.set(id, c);
  }
  return merged;
}

function readPersistedBlob(): PersistedBlob | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const o = JSON.parse(raw) as unknown;
    if (!o || typeof o !== 'object') {
      return null;
    }
    const rec = o as Record<string, unknown>;
    if (!isLaneArray(rec['planned']) || !isLaneArray(rec['active']) || !isLaneArray(rec['done'])) {
      return null;
    }
    return {
      planned: rec['planned'],
      active: rec['active'],
      done: rec['done'],
      ...(rec['customCards'] && typeof rec['customCards'] === 'object'
        ? { customCards: rec['customCards'] as Record<string, unknown> }
        : {}),
    };
  } catch {
    return null;
  }
}

/**
 * Merges persisted lane id lists with the catalog: drops unknown ids, dedupes,
 * and appends any catalog entries not yet placed (new built-in cards, or repaired state).
 */
export function nowBoardMergeWithCatalog(
  saved: NowBoardLanes | null,
  catalog: Map<string, NowRoadmapCard>,
): NowBoardLanes {
  const defaults = nowBoardDefaultLanes();
  const seen = new Set<string>();

  const cleanLane = (ids: string[]): string[] => {
    const out: string[] = [];
    for (const id of ids) {
      if (!catalog.has(id) || seen.has(id)) {
        continue;
      }
      seen.add(id);
      out.push(id);
    }
    return out;
  };

  let planned: string[];
  let active: string[];
  let done: string[];

  if (saved) {
    planned = cleanLane(saved.planned);
    active = cleanLane(saved.active);
    done = cleanLane(saved.done);
  } else {
    planned = [...defaults.planned];
    active = [...defaults.active];
    done = [...defaults.done];
    for (const id of planned) {
      seen.add(id);
    }
    for (const id of active) {
      seen.add(id);
    }
    for (const id of done) {
      seen.add(id);
    }
  }

  const appendMissing = (lane: NowRoadmapLane, ids: string[]): void => {
    for (const id of ids) {
      if (seen.has(id) || !catalog.has(id)) {
        continue;
      }
      if (nowRoadmapDefaultLane(id) === lane) {
        seen.add(id);
        if (lane === 'planned') {
          planned.push(id);
        } else if (lane === 'active') {
          active.push(id);
        } else {
          done.push(id);
        }
      }
    }
  };

  appendMissing('planned', defaults.planned);
  appendMissing('active', defaults.active);
  appendMissing('done', defaults.done);

  for (const id of catalog.keys()) {
    if (!seen.has(id)) {
      const lane = nowRoadmapDefaultLane(id);
      seen.add(id);
      if (lane === 'planned') {
        planned.push(id);
      } else if (lane === 'active') {
        active.push(id);
      } else {
        done.push(id);
      }
    }
  }

  return { planned, active, done };
}

export function nowBoardReadLanes(): NowBoardLanes {
  const blob = readPersistedBlob();
  const catalog = nowBoardFullCatalog();
  const saved: NowBoardLanes | null = blob
    ? { planned: blob.planned, active: blob.active, done: blob.done }
    : null;
  return nowBoardMergeWithCatalog(saved, catalog);
}

function readCustomCardsOnly(): Map<string, NowRoadmapCard> {
  return parseCustomCards(readPersistedBlob()?.customCards);
}

export function nowBoardWritePersisted(lanes: NowBoardLanes, custom: Map<string, NowRoadmapCard>): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  try {
    const customCards: Record<string, NowRoadmapCard> = {};
    for (const [id, c] of custom) {
      customCards[id] = c;
    }
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        planned: lanes.planned,
        active: lanes.active,
        done: lanes.done,
        ...(custom.size ? { customCards } : {}),
      }),
    );
  } catch {
    /* quota / private mode */
  }
}

export function nowBoardWriteLanes(lanes: NowBoardLanes): void {
  nowBoardWritePersisted(lanes, readCustomCardsOnly());
}

export function nowBoardAddCustomCard(card: NowRoadmapCard, lane: NowRoadmapLane): void {
  const blob = readPersistedBlob();
  const custom = readCustomCardsOnly();
  custom.set(card.id, card);
  const catalog = nowRoadmapStaticCardById();
  for (const [id, c] of custom) {
    catalog.set(id, c);
  }
  const saved: NowBoardLanes | null = blob
    ? { planned: blob.planned, active: blob.active, done: blob.done }
    : null;
  let lanes = nowBoardMergeWithCatalog(saved, catalog);
  lanes = {
    planned: lanes.planned.filter((id) => id !== card.id),
    active: lanes.active.filter((id) => id !== card.id),
    done: lanes.done.filter((id) => id !== card.id),
  };
  const key = lane === 'planned' ? 'planned' : lane === 'active' ? 'active' : 'done';
  lanes = { ...lanes, [key]: [...lanes[key], card.id] };
  nowBoardWritePersisted(lanes, custom);
}

export function newCustomNowCardId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return `now-custom-${crypto.randomUUID()}`;
  }
  return `now-custom-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}
