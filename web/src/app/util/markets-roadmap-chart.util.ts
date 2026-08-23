import {
  JourneyDirection,
  MarketsJourneyDto,
  MarketsJourneyEntryDto,
  MarketsJourneyLiveSeriesPointDto,
} from '../models/markets-journey.models';

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

interface ChartRow {
  date: string;
  label: string;
  actual: number | null;
  target: number | null;
  dayChange: number | null;
  entry: MarketsJourneyEntryDto;
}

export function buildRoadmapChartPoints(j: MarketsJourneyDto | null | undefined): RoadmapChartPoint[] {
  if (!j) {
    return [];
  }
  const series = j.liveNet?.history?.length ? j.liveNet.history : (j.liveNet?.series ?? []);
  const rows = mergeChartRows(series, j.entries ?? []);
  if (!rows.length) {
    return [];
  }
  const values: number[] = [0, Number(j.milestoneAmount) || 0];
  for (const row of rows) {
    if (row.target != null) {
      values.push(row.target);
    }
    if (row.actual != null) {
      values.push(row.actual);
    }
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const pad = 6;
  const n = rows.length;
  const labelEvery = n <= 8 ? 1 : Math.ceil(n / 6);
  return rows.map((row, i) => {
    const x = n === 1 ? 50 : pad + (i * (100 - pad * 2)) / (n - 1);
    const toY = (v: number | null) =>
      v == null ? null : 100 - pad - ((Number(v) - min) / span) * (100 - pad * 2);
    return {
      entry: withDirection(row),
      x,
      targetY: toY(row.target),
      actualY: toY(row.actual),
      label: row.label,
      showLabel: i === 0 || i === n - 1 || i % labelEvery === 0,
      selected: j.liveNet?.asOfDate != null && row.date === j.liveNet.asOfDate,
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
  const series =
    j.liveNet?.history?.length ? j.liveNet.history : (j.liveNet?.series ?? []);
  for (const s of series) {
    values.push(Number(s.total));
  }
  for (const e of j.entries ?? []) {
    if (e.targetAmount != null) {
      values.push(Number(e.targetAmount));
    }
    if (e.actualAmount != null && !series.length) {
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

function mergeChartRows(
  series: MarketsJourneyLiveSeriesPointDto[],
  entries: MarketsJourneyEntryDto[],
): ChartRow[] {
  const byDate = new Map<string, ChartRow>();
  for (const point of series) {
    byDate.set(point.date, {
      date: point.date,
      label: point.date,
      actual: Number(point.total),
      target: null,
      dayChange: point.dayChange,
      entry: seriesEntry(point),
    });
  }
  for (const entry of entries) {
    const existing = byDate.get(entry.periodDate);
    if (existing) {
      if (entry.targetAmount != null) {
        existing.target = Number(entry.targetAmount);
      }
      if (entry.periodLabel) {
        existing.label = entry.periodLabel;
      }
      existing.entry = {
        ...existing.entry,
        ...entry,
        actualAmount: existing.actual,
        direction: existing.entry.direction,
      };
      continue;
    }
    if (entry.actualAmount == null && entry.targetAmount == null) {
      continue;
    }
    byDate.set(entry.periodDate, {
      date: entry.periodDate,
      label: entry.periodLabel || entry.periodDate,
      actual: entry.actualAmount,
      target: entry.targetAmount,
      dayChange: null,
      entry,
    });
  }
  return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));
}

function seriesEntry(point: MarketsJourneyLiveSeriesPointDto): MarketsJourneyEntryDto {
  return {
    id: Number(point.date.replaceAll('-', '')),
    periodDate: point.date,
    periodLabel: point.date,
    targetAmount: null,
    actualAmount: point.total,
    targetNote: '',
    actualNote: '',
    variance: point.dayChange,
    direction: directionFromChange(point.dayChange),
    createdAt: '',
    updatedAt: '',
  };
}

function withDirection(row: ChartRow): MarketsJourneyEntryDto {
  if (row.dayChange == null) {
    return row.entry;
  }
  return { ...row.entry, direction: directionFromChange(row.dayChange), variance: row.dayChange };
}

function directionFromChange(change: number | null | undefined): JourneyDirection {
  if (change == null) {
    return 'UNKNOWN';
  }
  if (change > 0) {
    return 'ABOVE';
  }
  if (change < 0) {
    return 'BELOW';
  }
  return 'ON';
}
