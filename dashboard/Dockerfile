FROM node:22-alpine AS build
WORKDIR /app

COPY dashboard/package.json dashboard/package-lock.json ./
RUN npm ci

COPY dashboard/index.html ./
COPY dashboard/vite.config.ts ./
COPY dashboard/tsconfig.json ./
COPY dashboard/tsconfig.node.json ./
COPY dashboard/public public
COPY dashboard/scripts scripts
COPY dashboard/src src

RUN npm run build

FROM nginx:1.27-alpine

COPY dashboard/nginx.conf /etc/nginx/conf.d/default.conf
COPY dashboard/docker-entrypoint.sh /docker-entrypoint.d/40-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/40-runtime-config.sh

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
