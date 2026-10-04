FROM node:22-alpine AS build
WORKDIR /app/web/frontend
COPY web/frontend/package*.json ./
RUN npm ci
COPY web/frontend/ ./
COPY web/catalog/options/de.json web/catalog/options/en.json /app/web/catalog/options/
COPY web/catalog/options/contract.json web/catalog/options/configuration.schema.json /app/web/catalog/options/
COPY web/catalog/dar/generated/catalog.json /app/web/catalog/dar/generated/
RUN npm run build
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY web/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/web/frontend/dist /usr/share/nginx/html
