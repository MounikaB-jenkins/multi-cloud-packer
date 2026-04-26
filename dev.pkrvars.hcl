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
