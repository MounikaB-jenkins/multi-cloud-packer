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

source "amazon-ebs" "aws" {
  region        = var.region
  instance_type = var.instance_type
  ami_name      = var.image_name
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
source "googlecompute" "gcp" {
  project_id  = var.gcp_project
  zone        = "us-central1-a"
  machine_type = "n1-standard-1"

  source_image_family = "debian-10"
  source_image_project_id = "debian-cloud"

  ssh_username = "packer"
  image_name   = var.image_name
}

build {
  sources = ["source.amazon-ebs.aws", "source.googlecompute.gcp"]

  provisioner "shell" {
    script = "install_nginx.sh"
  }
}
