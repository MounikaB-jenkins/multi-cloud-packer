#Variables
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

# AWS AMI Distribution Settings
# Add the AWS regions you want to copy the AMI to within the same account
aws_ami_regions = ["us-west-2", "eu-central-1"]
# Add the 12-digit AWS Account IDs you want to share the AMI with
aws_ami_users   = ["123456789012", "987654321098"]
