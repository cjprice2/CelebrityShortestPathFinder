# syntax=docker/dockerfile:1.6

# Base stage with updated npm (cached layer)
FROM node:22.19.0-alpine AS base
RUN npm install -g npm@11.6.0

# Dependencies stage (all dependencies for build)
FROM base AS deps
WORKDIR /app
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci


# Build stage
FROM base AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
# Copy only necessary files for build (exclude node_modules, .next, etc.)
COPY package*.json ./
COPY next.config.mjs ./
COPY postcss.config.mjs ./
COPY jsconfig.json ./
COPY eslint.config.mjs ./
COPY src ./src
COPY public ./public
# Ensure Next rewrites target the backend service name inside Docker
ENV NEXT_PUBLIC_API_URL=http://backend:8080
RUN --mount=type=cache,target=/app/.next/cache npm run build

# Runtime stage - Use standalone Next.js server (minimal dependencies)
FROM base AS runtime
ENV NODE_ENV=production
ENV NEXT_PUBLIC_API_URL=http://backend:8080
WORKDIR /app

# Copy the standalone server (includes only needed dependencies)
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

ENV PORT=3000
EXPOSE 3000
CMD ["node", "server.js"]
