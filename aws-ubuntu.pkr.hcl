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
    azure = {
      source  = "github.com/hashicorp/azure"
      version = "~> 1"
    }
  }
}

# ---------- Variables ----------
variable "region" {
  type    = string
  default = "us-east-1"
}

variable "instance_type" {
  type    = string
  default = "t2.micro"
}

variable "image_name" {
  type    = string
  default = "multi-cloud-ubuntu"
}

variable "gcp_project" {
  type    = string
  default = "packer-demo-456789"
}

variable "gcp_zone" {
  type    = string
  default = "us-central1-a"
}

variable "azure_location" {
  type    = string
  default = "East US"
}

variable "azure_resource_group" {
  type    = string
  default = "packer-resources"
}

variable "ssh_username" {
  type    = string
  default = "ubuntu"
}

# ---------- AWS (UBUNTU) ----------
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

  ssh_username = var.ssh_username
  ami_name     = "${var.image_name}-aws-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  tags = {
    Name        = var.image_name
    Environment = "dev"
    CreatedBy   = "packer"
    Cloud       = "aws"
  }
}

# ---------- GCP (UBUNTU) ----------
source "googlecompute" "gcp" {
  project_id          = var.gcp_project
  zone                = var.gcp_zone
  machine_type        = "e2-micro"
  source_image_family = "ubuntu-2204-lts"
  ssh_username        = var.ssh_username
  image_name          = "${var.image_name}-gcp-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  tags = ["http-server", "https-server", var.image_name]

  network = "global/networks/default"
}

# ---------- Azure (UBUNTU) ----------
source "azure-arm" "azure" {
  location            = var.azure_location
  resource_group_name = var.azure_resource_group
  vm_size             = "Standard_B1s"

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-focal"
    sku       = "20_04-lts-gen2"
    version   = "latest"
  }

  os_type         = "Linux"
  image_name      = "${var.image_name}-azure-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  ssh_username    = var.ssh_username
  ssh_password    = "" # Disable password auth
  ssh_timeout     = "20m"

  tags = {
    Environment = "dev"
    CreatedBy   = "packer"
    Cloud       = "azure"
  }
}

# ---------- BUILD ----------
build {
  sources = [
    "source.amazon-ebs.aws",
    "source.googlecompute.gcp",
    "source.azure-arm.azure"
  ]

  provisioner "shell" {
    script       = "scripts/install_nginx.sh"
    execute_command = "sudo sh -c '{{ .Vars }} {{ .Path }}'"
  }

  post-processor "manifest" {
    output     = "manifest.json"
    strip_path = true
  }
}
