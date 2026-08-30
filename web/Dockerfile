# Build with repository root as context:
#   docker build -f web/Dockerfile .
FROM node:22-alpine AS build
WORKDIR /app
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN printf "export const WEB_PUBLISHED_AT = '%s';\n" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > src/app/web-published-at.ts \
  && npm run build

FROM nginx:1.27-alpine
COPY web/nginx-docker.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/web/browser /usr/share/nginx/html
