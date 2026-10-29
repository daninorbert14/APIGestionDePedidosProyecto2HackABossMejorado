# Etapa 1: generar los estáticos con Node. Esta imagen no llega a producción.
FROM node:22-alpine AS build
WORKDIR /app
# Los manifiestos primero: Docker reutiliza la caché de capas y npm ci solo
# se re-ejecuta si cambia el package-lock.json (instalación reproducible).
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite incrusta VITE_API_URL al construir: tiene que llegar como argumento.
# Sin valor por defecto a propósito: si se olvida, el build pasa igual pero la
# app queda muda (llama a rutas relativas) y el fallo aparece en runtime.
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Etapa 2: solo nginx sirviendo estáticos; sin Node ni código fuente.
FROM nginx:alpine
# Config propia: el fallback de SPA (try_files) no viene en la imagen base.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
