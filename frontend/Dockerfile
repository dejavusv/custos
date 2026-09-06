# -----------------------------------------------------------
# Stage 1: Build React Application with Node 20
# -----------------------------------------------------------
FROM node:20-alpine AS builder

WORKDIR /app

# Copy dependency specifications
COPY package.json package-lock.json* ./
RUN npm ci || npm install

# Copy source code and build production assets
COPY . .
RUN npm run build

# -----------------------------------------------------------
# Stage 2: Production Nginx Server (Lightweight)
# -----------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

LABEL maintainer="Custos Platform Team <ops@custos.io>"
LABEL description="Custos Web UI Dashboard & Live Terminal"

# Install curl for healthcheck
RUN apk add --no-cache curl

# Remove default nginx static assets
RUN rm -rf /usr/share/nginx/html/*

# Copy custom Nginx reverse proxy configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy built frontend assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
