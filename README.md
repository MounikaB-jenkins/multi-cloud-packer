# Multi-Cloud Packer Pipeline

Build and deploy cloud images across AWS, GCP, and Azure using Packer and Jenkins.

## 📋 Project Overview

This project provides a **unified Jenkins pipeline** for building and deploying VM images across multiple cloud providers:
- **AWS**: Amazon Machine Images (AMI)
- **GCP**: Google Compute Engine Images
- **Azure**: Azure Managed Images

## 📁 Project Structure

```
.
├── Jenkinsfile                 # Jenkins pipeline definition
├── aws-ubuntu.pkr.hcl         # Packer template for all clouds
├── dev.pkrvars.hcl            # Development variables
├── install_nginx.sh           # Installation script (Nginx)
├── pre-flight-check.sh        # Pre-flight validation (Linux/Mac)
├── pre-flight-check.bat       # Pre-flight validation (Windows)
├── jenkins-job-config.xml     # Jenkins job configuration
├── QUICK_START.md             # 5-minute setup guide
├── JENKINS_SETUP.md           # Detailed Jenkins setup
└── README.md                  # This file
```

## 🚀 Quick Start

### 1. Prerequisites
- Jenkins with Pipeline plugin
- Packer installed
- AWS, GCP, and Azure CLI tools
- Git configured

### 2. Validate System
```bash
# Windows
pre-flight-check.bat

# Linux/Mac
bash pre-flight-check.sh
```

### 3. Set Up Jenkins Job
See **[QUICK_START.md](QUICK_START.md)** for step-by-step instructions.

### 4. Run Pipeline
1. Open Jenkins → Job: `multi-cloud-packer-pipeline`
2. Click **Build with Parameters**
3. Configure parameters:
   - `IMAGE_NAME`: e.g., `multi-cloud-ubuntu`
   - `BUILD_AWS`, `BUILD_GCP`, `BUILD_AZURE`: Enable providers
   - `DEPLOY_*`: Enable deployments
4. Click **Build**

---

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| [QUICK_START.md](QUICK_START.md) | Fast 5-minute setup guide |
| [JENKINS_SETUP.md](JENKINS_SETUP.md) | Detailed configuration instructions |
| [Jenkinsfile](Jenkinsfile) | Pipeline definition (source code) |

---

## 🔄 Pipeline Stages

### 1. **Checkout**
Clones the repository from GitHub.

### 2. **Init & Validate**
- Initializes Packer plugins
- Validates Packer template syntax

### 3. **Build Images**
Builds images for selected cloud providers:
- AWS: Packer → AMI
- GCP: Packer → GCE Image
- Azure: Packer → Azure Managed Image

### 4. **Extract Image IDs**
Parses `manifest.json` to extract:
- AMI ID (AWS)
- Image Name (GCP)
- Image ID (Azure)

### 5. **Deploy Instances** (Parallel)
Deploys instances to cloud providers:
- **AWS EC2**: Launches instance from AMI
- **GCP VM**: Creates compute instance
- **Azure VM**: Creates virtual machine

---

## 📊 Pipeline Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `IMAGE_NAME` | String | multi-cloud-ubuntu | Image name across clouds |
| `AWS_REGION` | String | us-east-1 | AWS region |
| `GCP_PROJECT` | String | packer-demo-456789 | GCP project ID |
| `GCP_ZONE` | String | us-central1-a | GCP zone |
| `AZURE_LOCATION` | String | East US | Azure region |
| `AZURE_RESOURCE_GROUP` | String | packer-resources | Azure resource group |
| `BUILD_AWS` | Boolean | true | Build AWS image |
| `BUILD_GCP` | Boolean | true | Build GCP image |
| `BUILD_AZURE` | Boolean | true | Build Azure image |
| `DEPLOY_AWS` | Boolean | true | Deploy AWS instance |
| `DEPLOY_GCP` | Boolean | true | Deploy GCP instance |
| `DEPLOY_AZURE` | Boolean | true | Deploy Azure instance |

---

## 🔐 Required Credentials

Set up these in Jenkins **Manage Credentials**:

| ID | Type | Contents |
|---|---|---|
| `github-token` | Secret text | GitHub Personal Access Token |
| `aws-creds` | Username + Password | AWS Access Key ID + Secret |
| `gcp-key` | Secret file | GCP Service Account JSON |
| `azure-creds` | Username + Password | Azure Client ID + Secret |

**Environment Variables:**
- `AZURE_SUBSCRIPTION_ID`: Azure subscription ID
- `AZURE_TENANT_ID`: Azure tenant ID

---

## 🛠️ Environment Variables

Available in pipeline:

| Variable | Source |
|----------|--------|
| `IMAGE_NAME` | Parameter |
| `AWS_REGION` | Parameter |
| `APP_NAME` | Environment (multi-cloud-nginx) |
| `ENV` | Environment (dev) |
| `OWNER` | Environment (jenkins) |
| `AMI_ID` | Extracted from manifest.json |
| `GCP_IMAGE` | Extracted from manifest.json |
| `AZURE_IMAGE` | Extracted from manifest.json |

---

## 📝 Packer Template

**File**: [aws-ubuntu.pkr.hcl](aws-ubuntu.pkr.hcl)

Features:
- Multi-cloud support (AWS, GCP, Azure)
- Ubuntu 20.04 base image
- Nginx installation via [install_nginx.sh](install_nginx.sh)
- Configurable via [dev.pkrvars.hcl](dev.pkrvars.hcl)

---

## 📈 Build Output

After successful build:

1. **Artifacts**:
   - `manifest.json` - Archived in Jenkins
   
2. **Cloud Provider Outputs**:
   - **AWS**: AMI available in EC2 Dashboard
   - **GCP**: Image in Compute Engine Images
   - **Azure**: Image in Azure Portal (Shared Image Gallery)

3. **Deployed Resources**:
   - **AWS**: EC2 instance running
   - **GCP**: Compute instance running
   - **Azure**: Virtual machine running

---

## 🔍 Monitoring & Troubleshooting

### View Build Logs
1. Jenkins job → Click build number
2. Click **Console Output**
3. Logs stream in real-time

### Common Issues

| Issue | Solution |
|-------|----------|
| GitHub checkout fails | Verify `github-token` credential |
| Packer not found | Add Packer to system PATH |
| AWS build fails | Check AWS credentials and IAM permissions |
| GCP build fails | Verify GCP service account JSON |
| Azure build fails | Check Azure subscription and credentials |
| Deployment fails | Verify resource group/region exists |

For detailed troubleshooting, see [JENKINS_SETUP.md](JENKINS_SETUP.md#troubleshooting).

---

## 🌐 Accessing Deployed Resources

### AWS EC2
```bash
aws ec2 describe-instances --region us-east-1 \
  --filters "Name=tag:Environment,Values=dev"
```

### GCP Compute
```bash
gcloud compute instances list --zone=us-central1-a
```

### Azure VM
```bash
az vm list --resource-group packer-resources
```

---

## 🔄 Continuous Integration

### GitHub Webhook
Enable automatic builds on push:
1. GitHub → Settings → Webhooks → Add webhook
2. Payload URL: `http://jenkins-server:8080/github-webhook/`
3. Events: Push events
4. Active: ✓

Now any push to `main` branch triggers the pipeline!

---

## 📚 Additional Resources

- **Jenkins Pipeline**: https://www.jenkins.io/doc/book/pipeline/
- **Packer**: https://www.packer.io/docs
- **AWS Packer Plugin**: https://www.packer.io/plugins/builders/amazon
- **GCP Packer Plugin**: https://www.packer.io/plugins/builders/googlecompute
- **Azure Packer Plugin**: https://www.packer.io/plugins/builders/azure

---

## 👤 Author
Mounika B (MounikaB-jenkins)

---

## 📝 License
[Your License Here]

---

## 💡 Tips & Best Practices

1. **Test locally first**:
   ```bash
   packer validate aws-ubuntu.pkr.hcl
   packer build -var-file=dev.pkrvars.hcl aws-ubuntu.pkr.hcl
   ```

2. **Use parameters** for different environments:
   - Create additional `.pkrvars.hcl` files for prod, staging
   - Pass via Jenkins parameters

3. **Version your images**:
   - Use `IMAGE_NAME=multi-cloud-ubuntu-$(date +%Y%m%d)` for timestamps

4. **Monitor costs**:
   - Set auto-cleanup for old images
   - Use spot instances for builds (cost savings)

5. **Security hardening**:
   - Add additional provisioners in Packer template
   - Run security scanning post-build
   - Use AWS Systems Manager for patching

