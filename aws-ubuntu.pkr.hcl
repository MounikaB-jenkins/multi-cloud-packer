packer {
  required_plugins {
    amazon = {
      source  = "github.com/hashicorp/amazon"
      version = "~> 1"
    }
    googlecompute = {
      source  = "github.com/hashicorp/googlecompute"
      version = "~> 1"
    }
  }
}

# Variables
variable "region" {
  default = "us-east-1"
}

variable "instance_type" {
  default = "t2.micro"
}

variable "image_name" {
  default = "my-image"
}

variable "gcp_project" {
  default = "packer-demo-456789"
}

# AWS Source
source "amazon-ebs" "aws" {
  region        = var.region
  instance_type = var.instance_type

  source_ami_filter {
    filters = {
      name                = "amzn2-ami-hvm-*"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    owners      = ["amazon"]
    most_recent = true
  }

  ssh_username = "ec2-user"
  ami_name     = var.image_name
}

# GCP Source (FIXED)
source "googlecompute" "gcp" {
  project_id = var.gcp_project
  zone       = "us-central1-a"        # REQUIRED
  machine_type = "e2-micro"           # instead of instance_type

  source_image_family     = "debian-10"
  source_image_project_id = ["debian-cloud"]

  ssh_username = "packer"
  image_name   = var.image_name
}

# Build
build {
  sources = [
    "source.amazon-ebs.aws",
    "source.googlecompute.gcp"
  ]

  provisioner "shell" {
    script = "install_nginx.sh"
  }
}
