/** Default env; `ng serve` / dev builds replace this via angular.json → `environment.development.ts`. */
export const environment = {
  production: false,
  /** Empty = same-origin `/api` (reverse proxy in front of the API). */
  apiBaseUrl: '',
  /** MapLibre style JSON URL (swap for MapTiler / self-hosted in production). */
  travelMapStyleUrl: 'https://demotiles.maplibre.org/style.json',
};
