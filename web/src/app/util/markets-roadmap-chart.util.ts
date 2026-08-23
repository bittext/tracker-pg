import { MarketsJourneyDto, MarketsJourneyEntryDto } from '../models/markets-journey.models';

export interface RoadmapChartPoint {
  entry: MarketsJourneyEntryDto;
  x: number;
  targetY: number | null;
  actualY: number | null;
  label: string;
  showLabel: boolean;
  selected: boolean;
}

export interface RoadmapActualSegment {
  d: string;
  direction: 'ABOVE' | 'ON' | 'BELOW' | 'UNKNOWN';
}

export function buildRoadmapChartPoints(j: MarketsJourneyDto | null | undefined): RoadmapChartPoint[] {
  if (!j?.entries?.length) {
    return [];
  }
  const entries = j.entries;
  const values: number[] = [0, Number(j.milestoneAmount) || 0];
  for (const e of entries) {
    if (e.targetAmount != null) {
      values.push(Number(e.targetAmount));
    }
    if (e.actualAmount != null) {
      values.push(Number(e.actualAmount));
    }
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const pad = 6;
  const n = entries.length;
  return entries.map((e, i) => {
    const x = n === 1 ? 50 : pad + (i * (100 - pad * 2)) / (n - 1);
    const toY = (v: number | null) =>
      v == null ? null : 100 - pad - ((Number(v) - min) / span) * (100 - pad * 2);
    return {
      entry: e,
      x,
      targetY: toY(e.targetAmount),
      actualY: toY(e.actualAmount),
      label: e.periodLabel || e.periodDate,
      showLabel: true,
      selected: j.liveNet?.asOfDate != null && e.periodDate === j.liveNet.asOfDate,
    };
  });
}

export function roadmapTargetPath(pts: RoadmapChartPoint[]): string {
  const withTarget = pts.filter((p) => p.targetY != null);
  if (withTarget.length < 2) {
    return '';
  }
  return withTarget
    .map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${(p.targetY as number).toFixed(2)}`)
    .join(' ');
}

export function roadmapActualSegments(pts: RoadmapChartPoint[]): RoadmapActualSegment[] {
  const withActual = pts.filter((p) => p.actualY != null);
  const segs: RoadmapActualSegment[] = [];
  if (withActual.length === 1) {
    const b = withActual[0]!;
    segs.push({
      d: `M 6 ${(b.actualY as number).toFixed(2)} L ${b.x.toFixed(2)} ${(b.actualY as number).toFixed(2)}`,
      direction: b.entry.direction,
    });
    return segs;
  }
  for (let i = 1; i < withActual.length; i++) {
    const a = withActual[i - 1]!;
    const b = withActual[i]!;
    segs.push({
      d: `M ${a.x.toFixed(2)} ${(a.actualY as number).toFixed(2)} L ${b.x.toFixed(2)} ${(b.actualY as number).toFixed(2)}`,
      direction: b.entry.direction,
    });
  }
  return segs;
}

export function roadmapMilestoneY(j: MarketsJourneyDto | null | undefined, pts: RoadmapChartPoint[]): number | null {
  if (!j || !pts.length) {
    return null;
  }
  const values: number[] = [0, Number(j.milestoneAmount) || 0];
  for (const e of j.entries ?? []) {
    if (e.targetAmount != null) {
      values.push(Number(e.targetAmount));
    }
    if (e.actualAmount != null) {
      values.push(Number(e.actualAmount));
    }
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const pad = 6;
  const m = Number(j.milestoneAmount) || 0;
  return 100 - pad - ((m - min) / span) * (100 - pad * 2);
}

/** Prefer lowest sortOrder, then first in list. */
export function pickPrimaryJourney(rows: MarketsJourneyDto[]): MarketsJourneyDto | null {
  if (!rows.length) {
    return null;
  }
  return [...rows].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)[0] ?? null;
}
