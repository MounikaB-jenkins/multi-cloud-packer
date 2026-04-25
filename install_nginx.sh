#!/bin/bash

# Update
sudo yum update -y || sudo apt update -y

# Install nginx
sudo yum install -y nginx || sudo apt install -y nginx

# Start service
sudo systemctl start nginx
sudo systemctl enable nginx

# Custom page
echo "<h1>Hello from Multi-Cloud Image</h1>" | sudo tee /usr/share/nginx/html/index.html