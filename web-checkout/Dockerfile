FROM node:20-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
COPY web-painel/package.json web-painel/package.json
COPY web-checkout/package.json web-checkout/package.json
RUN npm ci

COPY web-checkout web-checkout
RUN npm run build --workspace web-checkout

FROM nginx:1.27-alpine

COPY web-checkout/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web-checkout/dist /usr/share/nginx/html

EXPOSE 8080

