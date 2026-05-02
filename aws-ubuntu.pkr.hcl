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

# ==================== VARIABLES ====================
variable "region" {
  type    = string
  default = "us-east-1"
  description = "AWS region"
}

variable "instance_type" {
  type    = string
  default = "t2.micro"
  description = "AWS instance type for building"
}

variable "image_name" {
  type    = string
  default = "multi-cloud-nginx"
  description = "Name for the built image"
}

variable "image_type" {
  type    = string
  default = "Linux"
  description = "OS type: Linux or Windows"
  
  validation {
    condition     = contains(["Linux", "Windows"], var.image_type)
    error_message = "Image type must be either Linux or Windows."
  }
}

variable "disable_public_ip" {
  type    = bool
  default = false
  description = "Disable public IP in deployments"
}

variable "gcp_project" {
  type    = string
  default = "packer-demo-456789"
  description = "GCP project ID"
}

variable "gcp_zone" {
  type    = string
  default = "us-central1-a"
  description = "GCP zone"
}

variable "azure_resource_group" {
  type    = string
  default = "packer-resources"
  description = "Azure resource group"
}

variable "azure_location" {
  type    = string
  default = "East US"
  description = "Azure location"
}

variable "azure_client_id" {
  type      = string
  default   = ""
  sensitive = true
  description = "Azure service principal client ID"
}

variable "azure_client_secret" {
  type      = string
  default   = ""
  sensitive = true
  description = "Azure service principal secret"
}

variable "azure_subscription_id" {
  type      = string
  default   = ""
  sensitive = true
  description = "Azure subscription ID"
}

variable "azure_tenant_id" {
  type      = string
  default   = ""
  sensitive = true
  description = "Azure tenant ID"
}

# ==================== LINUX SOURCES ====================
source "amazon-ebs" "aws_linux" {
  region            = var.region
  instance_type     = var.instance_type
  associate_public_ip_address = !var.disable_public_ip

  source_ami_filter {
    filters = {
      name                = "ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    owners      = ["099720109477"]
    most_recent = true
  }

  ssh_username = "ubuntu"
  ami_name     = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  tags = {
    Name        = var.image_name
    Environment = "dev"
    OSType      = "Linux"
    CreatedBy   = "packer"
  }
}

source "googlecompute" "gcp_linux" {
  project_id       = var.gcp_project
  zone             = var.gcp_zone
  machine_type     = "e2-micro"
  
  source_image_family     = "ubuntu-2204-lts"
  source_image_project_id = ["ubuntu-os-cloud"]
  
  network           = "global/networks/default"
  ssh_username      = "ubuntu"
  image_name        = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  omit_external_ip  = var.disable_public_ip
  use_internal_ip   = var.disable_public_ip

  image_labels = {
    name        = var.image_name
    environment = "dev"
    ostype      = "linux"
    createdby   = "packer"
  }
}

source "azure-arm" "azure_linux" {
  use_azure_cli_auth = false
  
  client_id       = var.azure_client_id
  client_secret   = var.azure_client_secret
  subscription_id = var.azure_subscription_id
  tenant_id       = var.azure_tenant_id

  os_type         = "Linux"
  image_publisher = "Canonical"
  image_offer     = "0001-com-ubuntu-server-jammy"
  image_sku       = "22_04-lts"
  
  location = var.azure_location
  vm_size  = "Standard_B2as_v2"

  managed_image_resource_group_name = var.azure_resource_group
  managed_image_name                = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  azure_tags = {
    Name        = var.image_name
    Environment = "dev"
    OSType      = "Linux"
    CreatedBy   = "packer"
  }
}

# ==================== WINDOWS SOURCES ====================
source "amazon-ebs" "aws_windows" {
  region            = var.region
  instance_type     = var.instance_type
  associate_public_ip_address = !var.disable_public_ip

  source_ami_filter {
    filters = {
      name                = "Windows_Server-2022-English-Core-*"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    owners      = ["801119661308"]
    most_recent = true
  }

  communicator   = "winrm"
  winrm_username = "Administrator"
  ami_name       = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  tags = {
    Name        = var.image_name
    Environment = "dev"
    OSType      = "Windows"
    CreatedBy   = "packer"
  }
}

source "googlecompute" "gcp_windows" {
  project_id       = var.gcp_project
  zone             = var.gcp_zone
  machine_type     = "e2-standard-2"
  
  source_image_family     = "windows-2022"
  source_image_project_id = ["windows-cloud"]
  
  network           = "global/networks/default"
  communicator      = "winrm"
  winrm_username    = "packer_user"
  image_name        = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  omit_external_ip  = var.disable_public_ip
  use_internal_ip   = var.disable_public_ip

  image_labels = {
    name        = var.image_name
    environment = "dev"
    ostype      = "windows"
    createdby   = "packer"
  }
}

source "azure-arm" "azure_windows" {
  use_azure_cli_auth = false
  
  client_id       = var.azure_client_id
  client_secret   = var.azure_client_secret
  subscription_id = var.azure_subscription_id
  tenant_id       = var.azure_tenant_id

  os_type         = "Windows"
  image_publisher = "MicrosoftWindowsServer"
  image_offer     = "WindowsServer"
  image_sku       = "2022-Datacenter"
  
  location = var.azure_location
  vm_size  = "Standard_B2s"

  communicator           = "winrm"
  winrm_username         = "packer"
  winrm_insecure         = true
  winrm_use_ssl          = true
  
  managed_image_resource_group_name = var.azure_resource_group
  managed_image_name                = "${var.image_name}-${formatdate("YYYYMMDDhhmmss", timestamp())}"

  azure_tags = {
    Name        = var.image_name
    Environment = "dev"
    OSType      = "Windows"
    CreatedBy   = "packer"
  }
}

# ==================== BUILD BLOCK ====================
build {
  sources = [
    "source.amazon-ebs.aws_linux",
    "source.googlecompute.gcp_linux",
    "source.azure-arm.azure_linux",
    "source.amazon-ebs.aws_windows",
    "source.googlecompute.gcp_windows",
    "source.azure-arm.azure_windows"
  ]

  # Linux provisioners
  provisioner "shell" {
    only = [
      "amazon-ebs.aws_linux",
      "googlecompute.gcp_linux"
    ]
    script = "install_nginx.sh"
  }

  provisioner "shell" {
    only   = ["azure-arm.azure_linux"]
    script = "install_nginx.sh"
  }

  provisioner "shell" {
    only   = ["azure-arm.azure_linux"]
    execute_command = "chmod +x {{ .Path }}; {{ .Vars }} sudo -E sh '{{ .Path }}'"
    inline = ["/usr/sbin/waagent -force -deprovision+user && export HISTSIZE=0 && sync"]
  }

  # Windows provisioners
  provisioner "powershell" {
    only   = ["amazon-ebs.aws_windows", "googlecompute.gcp_windows", "azure-arm.azure_windows"]
    script = "install_iis_windows.ps1"
  }

  post-processor "manifest" {
    output = "manifest.json"
    strip_path = true
  }
}
