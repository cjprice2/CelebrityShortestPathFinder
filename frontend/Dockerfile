# syntax=docker/dockerfile:1.6

# Dependencies stage
FROM node:20-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Ensure Next rewrites target the backend service name inside Docker
ENV NEXT_PUBLIC_API_URL=http://backend:8080
RUN --mount=type=cache,target=/app/.next/cache npm run build

# Runtime stage
FROM node:20-alpine AS runtime
ENV NODE_ENV=production
# Keep for client code that reads it
ENV NEXT_PUBLIC_API_URL=http://backend:8080
WORKDIR /app
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY --from=build /app/package*.json ./
RUN npm ci --omit=dev

ENV PORT=3000
EXPOSE 3000
CMD ["npm","run","start"]
