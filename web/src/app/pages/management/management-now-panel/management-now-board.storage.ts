import type { NowRoadmapLane } from './management-now-data';
import {
  NOW_ROADMAP_ACTIVE,
  NOW_ROADMAP_DONE,
  NOW_ROADMAP_PLANNED,
  nowRoadmapCardById,
  nowRoadmapDefaultLane,
} from './management-now-data';

const STORAGE_KEY = 'tracker-pg.management-now-board.v1';

export interface NowBoardLanes {
  readonly planned: string[];
  readonly active: string[];
  readonly done: string[];
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

function parseSaved(raw: string | null): NowBoardLanes | null {
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
    return { planned: rec['planned'], active: rec['active'], done: rec['done'] };
  } catch {
    return null;
  }
}

/**
 * Merges persisted lane id lists with the current catalog: drops unknown ids, dedupes,
 * and appends any new cards from the data file into their default lane.
 */
export function nowBoardMergeWithCatalog(saved: NowBoardLanes | null): NowBoardLanes {
  const catalog = nowRoadmapCardById();
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
  let saved: NowBoardLanes | null = null;
  if (typeof localStorage !== 'undefined') {
    saved = parseSaved(localStorage.getItem(STORAGE_KEY));
  }
  return nowBoardMergeWithCatalog(saved);
}

export function nowBoardWriteLanes(lanes: NowBoardLanes): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        planned: lanes.planned,
        active: lanes.active,
        done: lanes.done,
      }),
    );
  } catch {
    /* quota / private mode */
  }
}
