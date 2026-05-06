# Jenkins Pipeline Setup Guide

## Overview
This project uses two separate Jenkins pipelines to decouple the build process from multi-cloud instance management.

1. **AMI Creation Pipeline**: Builds AWS AMI, GCP Image, and Azure Managed Image using Packer.
2. **multi-cloud-Instance Spinup Pipeline**: Manages instance lifecycle (START/STOP) across all three clouds.

---

## 1. AMI Creation Pipeline

### Purpose
Triggers Packer to build images and securely stores the production SSH key in cloud-native secret managers.

### Jenkins Job Configuration
1. **New Item** → Pipeline
2. **Name**: `1-AMI-Creation`
3. **Pipeline Definition**: Pipeline script from SCM
4. **SCM**: Git
5. **Repository URL**: `https://github.com/MounikaB-jenkins/multi-cloud-packer.git`
6. **Script Path**: `Jenkinsfile.ami_creation`

### Key Features
- **Key Storage**: Automatically stores the generated SSH private key in:
  - **AWS**: Secrets Manager (`prod-key-<build>-secret`)
  - **GCP**: Secret Manager (`prod-key-<build>-secret`)
  - **Azure**: Key Vault (`prod-key-<build>-secret`)
- **Email Notification**: Sends the new Image IDs and the Secret Name to the specified email.

---

## 2. multi-cloud-Instance Spinup Pipeline

### Purpose
Launches or stops instances in AWS, GCP, or Azure using the images and secrets created by the build pipeline.

### Jenkins Job Configuration
1. **New Item** → Pipeline
2. **Name**: `multi-cloud-Instance Spinup`
3. **Pipeline Definition**: Pipeline script from SCM
4. **SCM**: Git
5. **Repository URL**: `https://github.com/MounikaB-jenkins/multi-cloud-packer.git`
6. **Script Path**: `Jenkinsfile.instance_spinup`

### Parameters
- `ACTION`: Choose `START` or `STOP`.
- `CLOUD`: Choose `AWS`, `GCP`, or `AZURE`.
- `IMAGE_ID`: The ID of the image to launch (e.g., `ami-12345`).
- `SECRET_NAME`: The name of the secret containing the SSH key (received via email from the build job).
- `INSTANCE_ID`: (Required for STOP) The ID of the instance to stop.
- `EMAIL`: Notification recipient.

---

## Prerequisites

### Jenkins Plugins
- **Pipeline**, **Git**, **Credentials Binding**
- **Email Extension Plugin**
- **Pipeline Utility Steps** (for `readJSON`)

### Cloud Credentials
- **Credential ID**: `aws-creds` (AWS Access/Secret)
- **Credential ID**: `gcp-key` (GCP JSON Key file)
- **Credential ID**: `azure-creds` (Azure Client ID/Secret)

---

## Running the Workflow

1. **Step 1**: Run `1-AMI-Creation`.
   - Packer builds the images.
   - SSH keys are generated and stored in cloud vaults.
   - Check your email for the **Image IDs** and **Secret Name**.

2. **Step 2**: Run `multi-cloud-Instance Spinup`.
   - Select `ACTION = START` and the target `CLOUD`.
   - Provide the `IMAGE_ID` and `SECRET_NAME` from Step 1.
   - The pipeline handles security groups (AWS) and firewall tags (GCP).
   - A Production VM is launched.
   - Check your email for the **Public IP address**.

3. **Step 3**: To stop the instance:
   - Run `multi-cloud-Instance Spinup`.
   - Select `ACTION = STOP` and provide the `INSTANCE_ID`.
   - Check your email for confirmation.
