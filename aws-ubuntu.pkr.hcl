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
      version = "~> 2"
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

variable "azure_resource_group" {
  default = "packer-resources"
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

  source_image_family  = "ubuntu-2004-lts"
  source_image_project_id = ["ubuntu-os-cloud"]

  ssh_username = "ubuntu"

  image_name = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  # IMPORTANT: adjust if no default network
  network    = "global/networks/default"

  image_labels = {
    name        = var.image_name
    environment = "dev"
    createdby   = "packer"
  }
}

# ---------------- AZURE (UBUNTU) ----------------
source "azure-arm" "azure" {
  use_azure_cli_auth = true

  os_type         = "Linux"
  image_publisher = "Canonical"
  image_offer     = "0001-com-ubuntu-server-focal"
  image_sku       = "20_04-lts"
  
  location = "East US"
  vm_size  = "Standard_B1s"

  managed_image_resource_group_name = var.azure_resource_group
  managed_image_name                = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  azure_tags = {
    Name        = var.image_name
    Environment = "dev"
    CreatedBy   = "packer"
  }
}

# ---------------- BUILD ----------------
build {
  sources = [
    "source.amazon-ebs.aws",
    "source.googlecompute.gcp",
    "source.azure-arm.azure"
  ]

  provisioner "shell" {
    script = "install_nginx.sh"
  }

  provisioner "shell" {
    execute_command = "chmod +x {{ .Path }}; {{ .Vars }} sudo -E sh '{{ .Path }}'"
    inline = [
      "/usr/sbin/waagent -force -deprovision+user && export HISTSIZE=0 && sync"
    ]
    only = ["azure-arm.azure"]
  }

  post-processor "manifest" {
    output = "manifest.json"
    strip_path = true
  }
}
#