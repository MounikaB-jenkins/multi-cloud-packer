pipeline {
    agent any

    parameters {
        choice(name: 'ACTION', choices: ['BUILD', 'DEPLOY', 'STOP'], description: 'The action to perform: BUILD a new image, DEPLOY an instance from an image, or STOP a running instance.')
        choice(name: 'CLOUD', choices: ['AWS', 'GCP', 'AZURE'], description: 'The target cloud provider.')
        
        // Build Parameters
        string(name: 'IMAGE_NAME', defaultValue: 'multi-cloud-image', description: 'Name for the Packer image (used for BUILD action).')
        choice(name: 'IMAGE_TYPE', choices: ['Linux', 'Windows'], description: 'Operating system type (used for BUILD action).')

        // Deploy/Stop Parameters
        string(name: 'IMAGE_ID', defaultValue: '', description: 'The Image ID to launch (AMI ID, GCP Image Name, Azure Image ID). Required for DEPLOY action.')
        string(name: 'INSTANCE_ID', defaultValue: '', description: 'The ID of the instance to stop. Required for STOP action.')
        string(name: 'SECRET_NAME', defaultValue: '', description: 'The name of the secret holding the SSH key (e.g., prod-key-1-secret). Required for DEPLOY action.')

        // Cloud Configuration
        string(name: 'AWS_REGION', defaultValue: 'us-east-1', description: 'AWS region for all operations.')
        string(name: 'AWS_VPC_ID', defaultValue: '', description: 'AWS VPC ID for Packer build and deployment (required if no default VPC exists).')
        string(name: 'AWS_SUBNET_ID', defaultValue: '', description: 'AWS Subnet ID for Packer build and deployment (required if no default VPC exists).')
        string(name: 'GCP_PROJECT', defaultValue: 'packer-demo-456789', description: 'GCP project ID')
        string(name: 'GCP_ZONE', defaultValue: 'us-central1-a', description: 'GCP zone for all operations.')
        string(name: 'AZURE_RESOURCE_GROUP', defaultValue: 'packer-resources', description: 'Azure resource group')
        string(name: 'AZURE_LOCATION', defaultValue: 'East US', description: 'Azure location for all operations.')
        string(name: 'AZURE_VAULT_NAME', defaultValue: 'packer-vault', description: 'Azure Key Vault name for storing/retrieving secrets.')
        string(name: 'AZURE_SUBSCRIPTION_ID', defaultValue: 'b943e408-73c1-4cea-b780-689120606f67', description: 'Azure subscription ID')
        string(name: 'AZURE_TENANT_ID', defaultValue: '8344e416-02b8-4b70-a912-1995cc408f19', description: 'Azure tenant ID')
        
        // Notifications
        string(name: 'EMAIL', defaultValue: 'mounika.b5693@outlook.com', description: 'Email for instance status notification')
    }

    environment {
        PACKER_TEMPLATE = 'aws-ubuntu.pkr.hcl'
        PACKER_EXE = 'packer'
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                git branch: 'main', credentialsId: 'github-token', url: 'https://github.com/MounikaB-jenkins/multi-cloud-packer.git'
            }
        }

        stage('Execute Action') {
            steps {
                script {

                    withCredentials([
                        usernamePassword(credentialsId: 'aws-creds', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY'),
                        file(credentialsId: 'gcp-key', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
                        usernamePassword(credentialsId: 'azure-creds', usernameVariable: 'ARM_CLIENT_ID', passwordVariable: 'ARM_CLIENT_SECRET')
                    ]) {
                        ensureAwsNetwork()

                        switch(params.ACTION) {
                            case 'BUILD':
                                buildImage()
                                break
                            case 'DEPLOY':
                                deployInstance()
                                break
                            case 'STOP':
                                stopInstance()
                                break
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                def subject = "[${params.CLOUD}] ${params.ACTION} Succeeded - Build #${BUILD_NUMBER}"
                def body = "Action '${params.ACTION}' for cloud '${params.CLOUD}' completed successfully.\n\n"
                if (params.ACTION == 'BUILD') {
                    body += "New Image ID: ${env.NEW_IMAGE_ID}\n"
                    body += "Secret Name: ${env.KEY_NAME}-secret\n"
                    if (params.CLOUD == 'AWS' && params.AWS_VPC_ID == '') {
                        body += "Auto-Provisioned VPC ID: ${env.ACTIVE_AWS_VPC_ID}\n"
                        body += "Auto-Provisioned Subnet ID: ${env.ACTIVE_AWS_SUBNET_ID}\n"
                    }
                } else if (params.ACTION == 'DEPLOY') {
                    body += "Instance ID: ${env.TARGET_INSTANCE_ID}\n"
                    body += "Public IP: ${env.PUBLIC_IP}\n"
                } else {
                    body += "Instance ID: ${params.INSTANCE_ID}\n"
                }
                emailext(subject: subject, body: body, to: "${params.EMAIL}")
            }
        }
        failure {
            script {
                def subject = "[${params.CLOUD}] ${params.ACTION} FAILED - Build #${BUILD_NUMBER}"
                def body = "Action '${params.ACTION}' for cloud '${params.CLOUD}' failed.\n\nCheck console output: ${BUILD_URL}console"
                emailext(subject: subject, body: body, to: "${params.EMAIL}")
            }
        }
        always {
            script {
                if (fileExists('private_key.pem')) { sh "rm -f private_key.pem" }
                if (fileExists('private_key.pem.pub')) { sh "rm -f private_key.pem.pub" }
            }
            archiveArtifacts artifacts: 'manifest.json', allowEmptyArchive: true
        }
    }
}

def buildImage() {
    def keyName = "prod-key-${BUILD_NUMBER}"
    env.KEY_NAME = keyName
    def privateKeyFile = "${WORKSPACE}/private_key.pem"
    def osType = params.IMAGE_TYPE.toLowerCase()
    def packerSource = ""

    // 1. Generate and Store SSH Key
    stage("Setup and Store Key for ${params.CLOUD}") {
        sh "ssh-keygen -t rsa -b 2048 -f private_key.pem -N \"\""
        switch(params.CLOUD) {
            case 'AWS':
                packerSource = "amazon-ebs.aws_${osType}"
                sh """
                aws ec2 import-key-pair --key-name ${keyName} --public-key-material fileb://private_key.pem.pub --region ${params.AWS_REGION}
                aws secretsmanager create-secret --name ${keyName}-secret --secret-string file://private_key.pem --region ${params.AWS_REGION}
                """
                break
            case 'GCP':
                packerSource = "googlecompute.gcp_${osType}"
                sh """
                gcloud secrets create ${keyName}-secret --replication-policy="automatic" --project=${params.GCP_PROJECT} || true
                gcloud secrets versions add ${keyName}-secret --data-file=private_key.pem --project=${params.GCP_PROJECT}
                """
                break
            case 'AZURE':
                packerSource = "azure-arm.azure_${osType}"
                sh """
                az login --service-principal -u \$ARM_CLIENT_ID -p \$ARM_CLIENT_SECRET --tenant ${params.AZURE_TENANT_ID}
                az account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                az keyvault secret set --vault-name ${params.AZURE_VAULT_NAME} --name ${keyName}-secret --file private_key.pem
                """
                break
        }
    }

    // 2. Run Packer Build
    stage("Build Image for ${params.CLOUD}") {
        try {
            sh """
            ${PACKER_EXE} init .
            ${PACKER_EXE} validate -only=${packerSource} ${PACKER_TEMPLATE}
            
            ${PACKER_EXE} build \\
                -only=${packerSource} \\
                -var "image_name=${params.IMAGE_NAME}" \\
                -var "image_type=${params.IMAGE_TYPE}" \\
                -var "region=${params.AWS_REGION}" \\
                -var "aws_vpc_id=${env.ACTIVE_AWS_VPC_ID}" \\
                -var "aws_subnet_id=${env.ACTIVE_AWS_SUBNET_ID}" \\
                -var "aws_key_name=${keyName}" \\
                -var "aws_private_key_file=${privateKeyFile}" \\
                -var "gcp_project=${params.GCP_PROJECT}" \\
                -var "gcp_zone=${params.GCP_ZONE}" \\
                -var "azure_resource_group=${params.AZURE_RESOURCE_GROUP}" \\
                -var "azure_location=${params.AZURE_LOCATION}" \\
                -var "azure_subscription_id=${params.AZURE_SUBSCRIPTION_ID}" \\
                -var "azure_tenant_id=${params.AZURE_TENANT_ID}" \\
                -var "azure_client_id=\$ARM_CLIENT_ID" \\
                -var "azure_client_secret=\$ARM_CLIENT_SECRET" \\
                ${PACKER_TEMPLATE}
            """
        } finally {
            // This ensures the key is deleted even if the build fails
            if (fileExists('private_key.pem')) { sh "rm -f private_key.pem" }
            if (fileExists('private_key.pem.pub')) { sh "rm -f private_key.pem.pub" }
        }
    }

    // 3. Extract Artifact ID
    stage('Extract Artifact ID') {
        if (fileExists('manifest.json')) {
            def manifest = readJSON file: 'manifest.json'
            def build = manifest.builds[0]
            def artifactId = build.artifact_id
            
            switch(params.CLOUD) {
                case 'AWS':
                    env.NEW_IMAGE_ID = artifactId.split(':')[1]
                    break
                case 'GCP':
                    env.NEW_IMAGE_ID = artifactId.split('/')[-1]
                    break
                case 'AZURE':
                    env.NEW_IMAGE_ID = artifactId
                    break;
            }
            echo "Successfully built ${params.CLOUD} image: ${env.NEW_IMAGE_ID}"
        } else {
            error "manifest.json not found. Packer build likely failed."
        }
    }
}

def deployInstance() {
    stage("Deploy Instance to ${params.CLOUD}") {
        switch(params.CLOUD) {
            case 'AWS':
                sh """
                VPC_FILTER=""
                VPC_ARG=""
                if [ -n "${env.ACTIVE_AWS_VPC_ID}" ]; then
                    VPC_FILTER="Name=vpc-id,Values=${env.ACTIVE_AWS_VPC_ID}"
                    VPC_ARG="--vpc-id ${env.ACTIVE_AWS_VPC_ID}"
                fi
                
                SG_ID=\$(aws ec2 describe-security-groups --filters "Name=group-name,Values=production-web-sg" \$VPC_FILTER --query "SecurityGroups[0].GroupId" --output text --region ${params.AWS_REGION} 2>/dev/null || echo "")
                if [ -z "\$SG_ID" ] || [ "\$SG_ID" = "None" ]; then
                    SG_ID=\$(aws ec2 create-security-group --group-name production-web-sg --description "Production Web SG" \$VPC_ARG --query "GroupId" --output text --region ${params.AWS_REGION})
                    aws ec2 authorize-security-group-ingress --group-id \$SG_ID --protocol tcp --port 22 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>/dev/null || true
                    aws ec2 authorize-security-group-ingress --group-id \$SG_ID --protocol tcp --port 80 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>/dev/null || true
                fi
                
                KEY_NAME="${params.SECRET_NAME.replace('-secret','')}"
                
                SUBNET_ARG=""
                if [ -n "${env.ACTIVE_AWS_SUBNET_ID}" ]; then
                    SUBNET_ARG="--subnet-id ${env.ACTIVE_AWS_SUBNET_ID}"
                fi
                
                INST_ID=\$(aws ec2 run-instances --image-id ${params.IMAGE_ID} --instance-type t2.micro --security-group-ids \$SG_ID --key-name \$KEY_NAME \$SUBNET_ARG --query "Instances[0].InstanceId" --output text --region ${params.AWS_REGION})
                aws ec2 wait instance-running --instance-ids \$INST_ID --region ${params.AWS_REGION}
                PUBLIC_IP=\$(aws ec2 describe-instances --instance-ids \$INST_ID --query "Reservations[0].Instances[0].PublicIpAddress" --output text --region ${params.AWS_REGION})
                
                echo "TARGET_INSTANCE_ID=\$INST_ID" > env.props
                echo "PUBLIC_IP=\$PUBLIC_IP" >> env.props
                """
                break
            case 'GCP':
                sh """
                INSTANCE_NAME="prod-vm-${BUILD_NUMBER}"
                gcloud compute firewall-rules create allow-ssh-http --allow tcp:22,tcp:80 --target-tags=prod-web --project=${params.GCP_PROJECT} 2>/dev/null || true
                
                gcloud compute instances create \$INSTANCE_NAME \\
                    --image=${params.IMAGE_ID} \\
                    --project=${params.GCP_PROJECT} \\
                    --zone=${params.GCP_ZONE} \\
                    --machine-type=e2-micro \\
                    --tags=prod-web \\
                    --format="get(networkInterfaces[0].accessConfigs[0].natIP)" > nat_ip.txt
                
                PUBLIC_IP=\$(cat nat_ip.txt)
                echo "TARGET_INSTANCE_ID=\$INSTANCE_NAME" > env.props
                echo "PUBLIC_IP=\$PUBLIC_IP" >> env.props
                """
                break
            case 'AZURE':
                sh """
                VM_NAME="prod-vm-${BUILD_NUMBER}"
                az login --service-principal -u \$ARM_CLIENT_ID -p \$ARM_CLIENT_SECRET --tenant ${params.AZURE_TENANT_ID}
                az account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                
                az vm create \\
                    --resource-group ${params.AZURE_RESOURCE_GROUP} \\
                    --name \$VM_NAME \\
                    --size Standard_B2ats_v2 \\
                    --image "${params.IMAGE_ID}" \\
                    --admin-username azureuser \\
                    --public-ip-sku Standard \\
                    --nsg-rule SSH \\
                    --query "{ip:publicIpAddress, id:id}" --output json > vm_info.json

                az vm open-port --resource-group ${params.AZURE_RESOURCE_GROUP} --name \$VM_NAME --port 80
                """
                def vmInfo = readJSON file: 'vm_info.json'
                env.TARGET_INSTANCE_ID = vmInfo.id
                env.PUBLIC_IP = vmInfo.ip
                return // Skip property reading for Azure
        }
        def props = readProperties file: 'env.props'
        env.TARGET_INSTANCE_ID = props['TARGET_INSTANCE_ID']
        env.PUBLIC_IP = props['PUBLIC_IP']
    }
}

def stopInstance() {
    stage("Stop Instance on ${params.CLOUD}") {
        if (params.INSTANCE_ID == '') {
            error "INSTANCE_ID parameter is required for STOP action."
        }
        switch(params.CLOUD) {
            case 'AWS':
                sh "aws ec2 stop-instances --instance-ids ${params.INSTANCE_ID} --region ${params.AWS_REGION}"
                break
            case 'GCP':
                sh "gcloud compute instances stop ${params.INSTANCE_ID} --project=${params.GCP_PROJECT} --zone=${params.GCP_ZONE}"
                break
            case 'AZURE':
                 sh """
                    az login --service-principal -u \$ARM_CLIENT_ID -p \$ARM_CLIENT_SECRET --tenant ${params.AZURE_TENANT_ID}
                    az account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                    az vm deallocate --resource-group ${params.AZURE_RESOURCE_GROUP} --name ${params.INSTANCE_ID}
                 """
                break
        }
        echo "Stop command issued for instance ${params.INSTANCE_ID} in ${params.CLOUD}."
    }
}

def ensureAwsNetwork() {
    def vpcId = params.AWS_VPC_ID != null ? params.AWS_VPC_ID.trim() : ''
    def subnetId = params.AWS_SUBNET_ID != null ? params.AWS_SUBNET_ID.trim() : ''
    
    env.ACTIVE_AWS_VPC_ID = vpcId
    env.ACTIVE_AWS_SUBNET_ID = subnetId
    
    if (params.CLOUD == 'AWS' && (params.ACTION == 'BUILD' || params.ACTION == 'DEPLOY')) {
        if (vpcId == '' || subnetId == '') {
            stage('Auto-Provision AWS Network') {
                echo "AWS_VPC_ID or AWS_SUBNET_ID is missing."
                echo "Checking for AWS Default VPC (Packer requires a Default VPC if no custom VPC is parameterized in the HCL)..."
                
                sh """
                DEF_VPC=\$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" --query "Vpcs[0].VpcId" --output text --region ${params.AWS_REGION} 2>/dev/null || echo "")
                
                if [ "\$DEF_VPC" = "None" ] || [ "\$DEF_VPC" = "null" ]; then
                    DEF_VPC=""
                fi
                
                if [ -z "\$DEF_VPC" ]; then
                    echo No Default VPC found. Restoring the AWS Default VPC in ${params.AWS_REGION}...
                    DEF_VPC=\$(aws ec2 create-default-vpc --query "Vpc.VpcId" --output text --region ${params.AWS_REGION} 2>/dev/null || echo "")
                    sleep 5
                else
                    echo "Found existing Default VPC: \$DEF_VPC"
                fi
                
                if [ "\$DEF_VPC" = "None" ] || [ "\$DEF_VPC" = "null" ]; then
                    DEF_VPC=""
                fi
                
                if [ -z "\$DEF_VPC" ]; then
                    echo [ERROR] Failed to find or restore the Default VPC.
                    echo Please manually create a VPC and provide AWS_VPC_ID and AWS_SUBNET_ID.
                    exit 1
                fi
                
                echo "\$DEF_VPC" > auto_vpc.txt
                
                DEF_SUBNET=\$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=\$DEF_VPC" --query "Subnets[0].SubnetId" --output text --region ${params.AWS_REGION} 2>/dev/null || echo "")
                
                if [ "\$DEF_SUBNET" = "None" ] || [ "\$DEF_SUBNET" = "null" ]; then
                    DEF_SUBNET=""
                fi
                
                echo "\$DEF_SUBNET" > auto_subnet.txt
                """
                
                env.ACTIVE_AWS_VPC_ID = readFile('auto_vpc.txt').trim()
                env.ACTIVE_AWS_SUBNET_ID = readFile('auto_subnet.txt').trim()
                
                echo "======================================================"
                echo "Active VPC ID: ${env.ACTIVE_AWS_VPC_ID}"
                echo "Active Subnet ID: ${env.ACTIVE_AWS_SUBNET_ID}"
                echo "======================================================"
            }
        }
    }
}
