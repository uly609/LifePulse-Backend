FROM node:22-alpine AS build

WORKDIR /web
COPY package.json package-lock.json* ./
RUN npm install
COPY index.html ./
COPY vite.config.js ./
COPY src ./src
RUN npm run build

FROM nginx:1.27
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/dist /usr/share/nginx/html
