/** Default env; `ng serve` / dev builds replace this via angular.json → `environment.development.ts`. */
export const environment = {
  production: false,
  /** Empty = same-origin `/api` (reverse proxy in front of the API). */
  apiBaseUrl: '',
};
