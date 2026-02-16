# ===========================================
# DOCKERFILE - Frontend React (Production Build)
# ===========================================

FROM node:20-alpine AS build
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build for production
RUN npm run build

# ===========================================
# Runtime - Just the built files
# ===========================================
# nginx will serve these files
# We don't need a runtime stage, just the dist/ folder
