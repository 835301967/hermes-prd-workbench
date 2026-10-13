FROM node:22.23.1-bookworm-slim AS build
WORKDIR /build
ENV CI=true
RUN npm install -g pnpm@11.9.0
COPY apps/web/package.json apps/web/pnpm-lock.yaml apps/web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY apps/web/index.html apps/web/tsconfig.json apps/web/vite.config.ts ./
COPY apps/web/src ./src
COPY apps/web/public ./public
ARG VITE_TELEMETRY_ENABLED=false
ENV VITE_TELEMETRY_ENABLED=$VITE_TELEMETRY_ENABLED
RUN pnpm build

FROM nginxinc/nginx-unprivileged:stable-alpine-slim@sha256:7f79f463c6eb0d0bf8782992bb9636434398e3e5848398805aef2c9b025d8609
USER root
RUN apk upgrade --no-cache
USER 101
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
