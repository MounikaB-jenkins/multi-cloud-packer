#!/bin/bash
set -e

echo "Detecting OS..."

# Detect OS type
if [ -f /etc/redhat-release ]; then
    echo "Running on Amazon Linux / RHEL based system"

    sudo yum update -y

    # Enable nginx repo for Amazon Linux
    if command -v amazon-linux-extras &> /dev/null; then
        sudo amazon-linux-extras enable nginx1 || true
    fi

    sudo yum install nginx -y

    NGINX_HTML_DIR="/usr/share/nginx/html"

else
    echo "Running on Debian / Ubuntu system (GCP)"

    sudo apt-get update -y
    sudo apt-get install -y nginx

    NGINX_HTML_DIR="/var/www/html"
fi

echo "Starting and enabling nginx..."

sudo systemctl start nginx
sudo systemctl enable nginx

echo "Deploying HTML page..."

echo "<h1>Hello from Multi-Cloud Image</h1>" | sudo tee ${NGINX_HTML_DIR}/index.html > /dev/null

echo "Nginx setup completed successfully!"
