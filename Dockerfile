# ── Etapa 1: Compilar (requereix JDK 17, no 25) ─────────────────────────────
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app
COPY . .
RUN chmod +x gradlew

# Compilar GWT → JavaScript. El resultat queda a html/build/dist/
RUN ./gradlew :html:dist --no-daemon

# ── Etapa 2: Nginx per servir els estàtics ───────────────────────────────────
FROM nginx:alpine

COPY --from=builder /app/html/build/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
