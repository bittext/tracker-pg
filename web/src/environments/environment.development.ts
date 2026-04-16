export const environment = {
  production: false,
  /**
   * Empty string = same-origin `/api/...` so `ng serve` + `proxy.conf.json` forwards to Spring (tracker-pg on :9091).
   * Set to `http://127.0.0.1:9091` only if you must bypass the proxy (requires CORS on the server).
   */
  apiBaseUrl: '',
};
