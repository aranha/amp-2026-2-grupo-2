# Estágio 1 — build: Node só existe aqui, para gerar os arquivos estáticos.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# As VITE_* são lidas no build e ficam embutidas no JavaScript. São URLs que o NAVEGADOR acessa,
# por isso localhost, e não os nomes de serviço do compose.
ARG VITE_OIDC_AUTHORITY=http://localhost:8180/realms/credito
ARG VITE_BFF_URL=http://localhost:8080
RUN npm run build

# Estágio 2 — runtime: só nginx e o dist/. Sem Node, sem node_modules, sem código-fonte.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
