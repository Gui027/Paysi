FROM node:20-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
COPY web-painel/package.json web-painel/package.json
COPY web-checkout/package.json web-checkout/package.json
RUN npm ci

COPY web-checkout web-checkout
COPY shared shared
# Vite embute VITE_API_URL no bundle em tempo de build (é um app estático servido pelo
# nginx abaixo, sem servidor Node em runtime) — por isso precisa vir como build arg, não
# como variável de ambiente do container. O default aqui é a rede de segurança caso o
# --build-arg não chegue até aqui (aconteceu em staging com o compose do Portainer) —
# sem ele o ARG fica em string vazia, não undefined, e o fallback do código nunca entra.
ARG VITE_API_URL=https://api.paysi.com.br
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build --workspace web-checkout

FROM nginx:1.27-alpine

COPY web-checkout/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web-checkout/dist /usr/share/nginx/html

EXPOSE 8080

