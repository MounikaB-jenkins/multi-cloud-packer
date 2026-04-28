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
  default = "multi-cloud-ubuntu"
}

variable "gcp_project" {
  default = "packer-demo-456789"
}

# ---------------- AWS (UBUNTU) ----------------
source "amazon-ebs" "aws" {
  region        = var.region
  instance_type = var.instance_type

  source_ami_filter {
    filters = {
      name                = "ubuntu/images/hvm-ssd/ubuntu-focal-20.04-amd64-server-*"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    owners      = ["099720109477"] # Canonical
    most_recent = true
  }

  ssh_username = "ubuntu"

  ami_name = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  tags = {
    Name        = var.image_name
    Environment = "dev"
    CreatedBy   = "packer"
  }
}

# ---------------- GCP (UBUNTU) ----------------
source "googlecompute" "gcp" {
  project_id   = var.gcp_project
  zone         = "us-central1-a"
  machine_type = "e2-micro"

  source_image_family  = "ubuntu-2204-lts"
  source_image_project_id = ["ubuntu-os-cloud"]

  ssh_username = "ubuntu"

  image_name = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  network = "global/networks/default"
}

# ---------------- BUILD ----------------
build {
  sources = [
    "source.amazon-ebs.aws",
    "source.googlecompute.gcp"
  ]

  provisioner "shell" {
    script = "install_nginx.sh"
  }

  post-processor "manifest" {
    output = "manifest.json"
    strip_path = true
  }
}
