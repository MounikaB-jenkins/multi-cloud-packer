#!/bin/bash

sudo yum update -y
sudo amazon-linux-extras enable nginx1
sudo yum install nginx -y

sudo systemctl start nginx
sudo systemctl enable nginx

echo "<h1>Hello! from Multi-Cloud Image</h1>" | sudo tee /usr/share/nginx/html/index.html
