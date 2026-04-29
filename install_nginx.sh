#!/bin/bash
set -euo pipefail

# Logging
exec > >(tee -a /var/log/packer-provision.log) 2>&1

echo "===== Starting Nginx Provisioning ====="

# Detect OS
OS_TYPE="unknown"
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS_TYPE=$ID
elif [ -f /etc/redhat-release ]; then
    OS_TYPE="rhel"
fi

echo "Detected OS: $OS_TYPE"

# Install Nginx
case $OS_TYPE in
    ubuntu|debian)
        echo "Installing Nginx on Debian/Ubuntu..."
        sudo apt-get update -y
        sudo apt-get install -y nginx
        NGINX_HTML_DIR="/var/www/html"
        ;;
    *)
        echo "Unsupported OS: $OS_TYPE"
        exit 1
        ;;
esac

# Start and enable Nginx
echo "Starting and enabling Nginx..."
sudo systemctl start nginx
sudo systemctl enable nginx

# Deploy custom HTML
echo "Deploying custom HTML..."
BUILD_TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
cat <<EOF | sudo tee ${NGINX_HTML_DIR}/index.html > /dev/null
<!DOCTYPE html>
<html>
<head>
    <title>Multi-Cloud Image</title>
    <style>
        body { font-family: Arial, sans-serif; text-align: center; margin-top: 50px; }
        h1 { color: #4285F4; }
    </style>
</head>
<body>
    <h1>Hello from Multi-Cloud Image</h1>
    <p>Built at: ${BUILD_TIMESTAMP}</p>
    <p>OS: ${OS_TYPE}</p>
    <p>Cloud: ${CLOUD_PROVIDER:-unknown}</p>
</body>
</html>
EOF

echo "Nginx provisioning completed successfully!"
