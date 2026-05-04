export type AdminProvisionRole = 'USER' | 'ADMIN';

export interface AdminCreateUserRequest {
  username: string;
  email: string;
  password: string;
  role: AdminProvisionRole;
  mfaEnabled: boolean;
  active: boolean;
}

export interface AdminCreatedUserResponse {
  id: number;
  username: string;
  email: string;
  role: string;
}
