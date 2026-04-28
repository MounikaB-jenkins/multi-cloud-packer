#!/bin/bash
set -e

echo "Running on Ubuntu..."

sudo apt-get update -y
sudo apt-get install -y nginx

sudo systemctl start nginx
sudo systemctl enable nginx

echo "<h1>Hello from Multi-Cloud Ubuntu Image</h1>" | sudo tee /var/www/html/index.html > /dev/null

echo "Nginx installed successfully!"
