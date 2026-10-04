FROM node:22-alpine AS build
WORKDIR /app/web/frontend
COPY web/frontend/package*.json ./
RUN npm ci
COPY web/frontend/ ./
COPY web/catalog/options/de.json web/catalog/options/en.json /app/web/catalog/options/
RUN npm run build
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY web/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/web/frontend/dist /usr/share/nginx/html
