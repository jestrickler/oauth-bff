# Dockerfile for nginx with environment variable support
FROM nginx:alpine

# Copy entrypoint script
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Copy nginx config (will be processed by entrypoint)
COPY nginx.conf /etc/nginx/nginx.conf.template

ENTRYPOINT ["/docker-entrypoint.sh"]
