FROM node:20-alpine AS dependencies

WORKDIR /workspace
COPY package.json package-lock.json ./
COPY web-painel/package.json web-painel/package.json
COPY web-checkout/package.json web-checkout/package.json
COPY web-site/package.json web-site/package.json
RUN npm ci

FROM dependencies AS build
COPY shared shared
COPY web-site web-site
RUN npm run build --workspace web-site

FROM node:20-alpine

ENV NODE_ENV=production
WORKDIR /workspace
COPY --from=dependencies /workspace/node_modules node_modules
COPY package.json package-lock.json ./
COPY web-site/package.json web-site/package.json
COPY --from=build /workspace/web-site/.next web-site/.next
COPY --from=build /workspace/web-site/public web-site/public

USER node
EXPOSE 3030
CMD ["npm", "run", "start", "--workspace", "web-site"]
