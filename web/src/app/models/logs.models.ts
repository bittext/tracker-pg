export interface ServerLogsDto {
  lines: string[];
  requestedLimit: number;
  returned: number;
  totalBuffered: number;
}
