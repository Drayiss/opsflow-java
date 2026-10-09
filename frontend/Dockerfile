FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_OIDC_AUTHORITY
ARG VITE_OIDC_CLIENT_ID
ARG VITE_OIDC_SCOPE
ARG VITE_OIDC_AUTHORIZATION_AUDIENCE
ARG VITE_API_URL
RUN npm run build
FROM nginxinc/nginx-unprivileged:1.28-alpine
ENV API_UPSTREAM=api:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8081
