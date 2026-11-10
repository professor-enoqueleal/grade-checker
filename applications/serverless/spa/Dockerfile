# SPA for serverless/docker-compose.yml (capability serverless-local-compose): production build served by nginx, which plays
# the part of CloudFront (index.html for the SPA routes, /api to the local HTTP API gateway).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# nginx reads as the user nginx: every served file readable, whatever its mode on the developer's disk
RUN chmod -R a+rX /usr/share/nginx/html
