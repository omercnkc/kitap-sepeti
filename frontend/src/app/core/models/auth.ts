/** OpenAPI `LoginRequest` */
export interface LoginRequest {
  email: string;
  password: string;
}

/** OpenAPI `RegisterRequest` */
export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
}

/** OpenAPI `RefreshRequest` */
export interface RefreshRequest {
  refreshToken: string;
}

/** OpenAPI `TokenResponse` */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

/** OpenAPI `UserResponse.role` */
export type UserRole = 'USER' | 'ADMIN';

/** OpenAPI `UserResponse.status` */
export type UserStatus = 'ACTIVE' | 'SUSPENDED';

/** OpenAPI `UserResponse` */
export interface UserResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: UserRole;
  status: UserStatus;
}

/** JWT payload (istemci yalnızca okur; imza doğrulanmaz) */
export interface JwtPayload {
  sub?: string;
  role?: string;
  exp?: number;
  iat?: number;
  [claim: string]: unknown;
}
