FROM node:20-alpine AS dependencies

WORKDIR /workspace
COPY package.json package-lock.json ./
COPY web-painel/package.json web-painel/package.json
COPY web-checkout/package.json web-checkout/package.json
RUN npm ci

FROM dependencies AS build
COPY web-painel web-painel
RUN npm run build --workspace web-painel

FROM node:20-alpine

ENV NODE_ENV=production
WORKDIR /workspace
COPY --from=dependencies /workspace/node_modules node_modules
COPY package.json package-lock.json ./
COPY web-painel/package.json web-painel/package.json
COPY --from=build /workspace/web-painel/.next web-painel/.next
COPY --from=build /workspace/web-painel/public web-painel/public

USER node
EXPOSE 3000
CMD ["npm", "run", "start", "--workspace", "web-painel"]

