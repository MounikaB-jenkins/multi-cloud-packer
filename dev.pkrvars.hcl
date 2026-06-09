#Variables
region        = "us-east-1"

instance_type = "t3.micro"

image_name    = "my-image"

gcp_project   = "packer-demo-456789"

# VPC Configuration (Required if no default VPC exists in the region)
aws_vpc_id    = ""
aws_subnet_id = ""

# AWS AMI Distribution Settings
# Add the AWS regions you want to copy the AMI to within the same account
aws_ami_regions = ["us-west-2", "eu-central-1"]
# Add the 12-digit AWS Account IDs you want to share the AMI with
aws_ami_users   = ["187711699561"]
