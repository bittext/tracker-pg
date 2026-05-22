export interface AuthLoginEventDto {
  id: number;
  eventType: string;
  userId: number | null;
  username: string;
  clientIp: string;
  locationLabel: string;
  userAgent: string;
  detail: string;
  createdAt: string;
}
