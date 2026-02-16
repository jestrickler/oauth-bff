#!/bin/sh
# Nginx entrypoint with environment variable substitution

# Get the port from environment, default to 3000
PORT=${PORT:-3000}

# Process template and start nginx
envsubst '${PORT}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

# Start nginx in foreground
nginx -g "daemon off;"
