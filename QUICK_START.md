# Quick Start Guide: Running Jenkins Pipeline

## 🚀 Fast Track Setup (5 Minutes)

### Step 1: Run Pre-Flight Checks
Before configuring Jenkins, verify your system is ready:

**Windows:**
```bash
cd c:\DevopsProject\multi-cloud-packer
pre-flight-check.bat
```

**Linux/Mac:**
```bash
cd /path/to/multi-cloud-packer
bash pre-flight-check.sh
```

### Step 2: Create Jenkins Credentials
1. Open Jenkins (http://your-jenkins-server:8080)
2. Go to **Manage Jenkins** → **Manage Credentials** → **System** → **Global Credentials**
3. Click **Add Credentials** and create the following:

#### GitHub Token
- Kind: **Secret text**
- Secret: *[Your GitHub Personal Access Token]*
- ID: `github-token`
- Description: GitHub Personal Access Token

#### AWS Credentials
- Kind: **Username with password**
- Username: *[AWS Access Key ID]*
- Password: *[AWS Secret Access Key]*
- ID: `aws-creds`
- Description: AWS Access Keys

#### GCP Service Account
- Kind: **Secret file**
- File: *[Your GCP service account JSON]*
- ID: `gcp-key`
- Description: GCP Service Account Key

#### Azure Credentials
- Kind: **Username with password**
- Username: *[Azure Client ID]*
- Password: *[Azure Client Secret]*
- ID: `azure-creds`
- Description: Azure Service Principal

### Step 3: Create Jenkins Pipeline Job

#### Option A: Using UI (Easiest)
1. Jenkins home → **+ New Item**
2. Enter job name: `multi-cloud-packer-pipeline`
3. Select **Pipeline**
4. Scroll down to **Pipeline** section
5. **Definition**: Select *Pipeline script from SCM*
6. **SCM**: Select *Git*
7. **Repository URL**: `https://github.com/MounikaB-jenkins/multi-cloud-packer.git`
8. **Credentials**: Select `github-token`
9. **Branch Specifier**: `*/main`
10. **Script Path**: `Jenkinsfile`
11. Click **Save**

#### Option B: Import XML Configuration
1. Create a folder for Jenkins jobs: `c:\Program Files\Jenkins\jobs\`
2. Create subfolder: `multi-cloud-packer-pipeline`
3. Copy `jenkins-job-config.xml` to that folder and rename to `config.xml`
4. Restart Jenkins service
5. Job will appear in Jenkins UI

### Step 4: Set Environment Variables (if needed)
In Jenkins, go to **Manage Jenkins** → **Configure System** → **Global properties**
Add Environment Variables:
```
AZURE_SUBSCRIPTION_ID = your-azure-subscription-id
AZURE_TENANT_ID = your-azure-tenant-id
```

### Step 5: Run the Pipeline
1. Open the job: `multi-cloud-packer-pipeline`
2. Click **Build with Parameters**
3. Adjust parameters as needed:
   - `IMAGE_NAME`: Name for your image
   - `AWS_REGION`: AWS region
   - Toggle `BUILD_AWS`, `BUILD_GCP`, `BUILD_AZURE` based on needs
   - Toggle `DEPLOY_AWS`, `DEPLOY_GCP`, `DEPLOY_AZURE` for deployments
4. Click **Build**

---

## 📊 Pipeline Stages Explained

| Stage | Purpose | Duration |
|-------|---------|----------|
| **Checkout** | Clone GitHub repo | ~10 sec |
| **Init & Validate** | Initialize & validate Packer | ~30 sec |
| **Build Images** | Build cloud images in parallel | 5-15 min |
| **Extract Image IDs** | Parse manifest.json for IDs | ~5 sec |
| **Deploy Instances** | Deploy to cloud providers | 2-5 min |

---

## 🔍 Monitoring Build Progress

### Real-Time Logs
1. Click on running build
2. Click **Console Output**
3. Logs update in real-time

### Expected Output Example:
```
[Pipeline] Start of Pipeline
[Pipeline] node
Running on Jenkins in /var/jenkins_home/workspace/multi-cloud-packer-pipeline
[Pipeline] stage
[Pipeline] { (Checkout)
[Pipeline] git
Cloning the remote Git repository
[...git clone logs...]
[Pipeline] stage
[Pipeline] { (Init & Validate)
[Pipeline] bat
[multi-cloud-packer-pipeline] Running batch script
C:\packer init .
...
[Pipeline] stage
[Pipeline] { (Build Images)
[...packer build logs...]
amazon-ebs.aws: output will be in this color.
[...long build process...]
amazon-ebs.aws: AMI: ami-0123456789abcdef0
[Pipeline] }
```

---

## ⚠️ Troubleshooting

### "Pipeline fails at Checkout"
```
Solution:
1. Verify GitHub token has 'repo' scope
2. Check GitHub 2FA is configured correctly
3. Test: git clone https://github.com/MounikaB-jenkins/multi-cloud-packer.git
```

### "Packer command not found"
```
Solution:
1. Verify Packer is in PATH
2. Or update PACKER path in Jenkinsfile
3. Test: packer version
```

### "AWS credentials rejected"
```
Solution:
1. Verify AWS Access Key ID and Secret are correct
2. Test: aws s3 ls (from Jenkins agent)
3. Check AWS region is valid
```

### "Build fails at image extraction"
```
Solution:
1. Check manifest.json is being created
2. Verify source names in manifest match Jenkinsfile
3. Test: packer build locally to verify manifest format
```

### "Deployment fails"
```
Solution:
1. Verify IAM permissions for EC2/GCP/Azure
2. Check resource group/project exists in cloud
3. Verify image IDs were extracted correctly
4. Check instance type is available in selected region
```

---

## 🔐 Security Best Practices

1. **Never commit credentials** to Git
2. **Use credential IDs** in Jenkinsfile (not actual secrets)
3. **Restrict job access** via Jenkins authorization
4. **Rotate credentials** periodically
5. **Enable Jenkins CSRF protection**
6. **Use HTTPS** for Jenkins URL
7. **Enable audit logging** for build activities

---

## 📈 Next Steps

After successful first run:

1. **Set up webhooks** for automatic builds on Git push:
   - GitHub repo → Settings → Webhooks
   - Payload URL: `http://your-jenkins:8080/github-webhook/`
   - Events: Push events

2. **Configure email notifications**:
   - Jenkins → Configure System → Email Notification
   - Set SMTP server details

3. **Integrate with monitoring**:
   - Track image IDs in your infrastructure tools
   - Monitor deployment status via cloud APIs
   - Set up alerts for failed builds

4. **Version your images**:
   - Use `IMAGE_NAME` parameter with timestamps
   - Maintain image history for rollback capability

---

## 📞 Support Resources

- **Jenkins Documentation**: https://www.jenkins.io/doc/
- **Packer Docs**: https://www.packer.io/docs
- **AWS Packer Plugin**: https://www.packer.io/plugins/builders/amazon
- **GCP Packer Plugin**: https://www.packer.io/plugins/builders/googlecompute
- **Azure Packer Plugin**: https://www.packer.io/plugins/builders/azure

