pipeline {
    agent any

    parameters {
        choice(name: 'ACTION', choices: ['BUILD', 'DEPLOY', 'STOP'], description: 'The action to perform: BUILD a new image, DEPLOY an instance from an image, or STOP a running instance.')
        choice(name: 'CLOUD', choices: ['AWS', 'GCP', 'AZURE'], description: 'The target cloud provider.')
        
        string(name: 'BRANCH_NAME', defaultValue: 'develop', description: 'Git branch to checkout and run (e.g., develop, main, feature/xyz).')
        // Build Parameters
        string(name: 'IMAGE_NAME', defaultValue: 'multi-cloud-image', description: 'Name for the Packer image (used for BUILD action).')
        choice(name: 'IMAGE_TYPE', choices: ['Linux', 'Windows'], description: 'Operating system type (used for BUILD action).')

        // Deploy/Stop Parameters
        string(name: 'IMAGE_ID', defaultValue: '', description: 'The Image ID to launch (AMI ID, GCP Image Name, Azure Image ID). Required for DEPLOY action.')
        string(name: 'INSTANCE_ID', defaultValue: '', description: 'The ID of the instance to stop. Required for STOP action.')
        string(name: 'BASTION_IP', defaultValue: '', description: 'Public IP of the Bastion Host. Required for SSH access to private instances.')
        string(name: 'SECRET_NAME', defaultValue: '', description: 'The name of the secret holding the SSH key (e.g., prod-key-1-secret). Required for DEPLOY action.')

        // MongoDB Deployment Parameters
        choice(name: 'MONGO_OP', choices: ['CREATE', 'INSERT', 'UPDATE', 'DELETE'], description: 'MongoDB operation to perform during DEPLOY.')
        string(name: 'MONGO_COLLECTION', defaultValue: 'sampleData', description: 'MongoDB collection to operate on.')
        string(name: 'MONGO_DOCUMENT', defaultValue: '{ "name": "John Doe", "status": "created", "role": "admin" }', description: 'MongoDB document(s) or query arguments for the operation (e.g., {"name":"John"} or {}, {$set:{"status":"updated"}}).')

        // Cloud Configuration
        string(name: 'AWS_REGION', defaultValue: 'us-east-1', description: 'AWS region for all operations.')
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
        PACKER_EXE = 'packer.exe'
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                git branch: params.BRANCH_NAME, credentialsId: 'github-token', url: 'https://github.com/MounikaB-jenkins/multi-cloud-packer.git'
            }
        }

        stage('Execute Action') {
            steps {
                script {
                    if (isUnix()) {
                        env.GCLOUD_EXE = "gcloud"
                        env.AZ_EXE = "az"
                    } else {
                    // Find gcloud executable
                    env.GCLOUD_EXE = bat(returnStdout: true, script: """@echo off
                    setlocal enabledelayedexpansion
                    set "GCLOUD_EXE_FOUND="
                    where gcloud >nul 2>&1
                    if !ERRORLEVEL! equ 0 (
                        set "GCLOUD_EXE_FOUND=gcloud"
                    ) else (
                        for %%p in ("C:\\Users\\vresh\\AppData\\Local\\Google\\Cloud SDK\\google-cloud-sdk\\bin" "C:\\Program Files (x86)\\Google\\Cloud SDK\\google-cloud-sdk\\bin" "C:\\Program Files\\Google\\Cloud SDK\\google-cloud-sdk\\bin" "%LocalAppData%\\Google\\Cloud SDK\\google-cloud-sdk\\bin") do (
                            if exist "%%~p\\gcloud.cmd" if "!GCLOUD_EXE_FOUND!"=="" set "GCLOUD_EXE_FOUND=%%~p\\gcloud.cmd"
                        )
                    )
                    if "!GCLOUD_EXE_FOUND!" neq "" echo !GCLOUD_EXE_FOUND!
                    exit /b 0
                    """).trim()

                    // Find az executable
                    env.AZ_EXE = bat(returnStdout: true, script: """@echo off
                    setlocal enabledelayedexpansion
                    set "AZ_EXE_FOUND="
                    for %%p in ("C:\\Program Files\\Microsoft SDKs\\Azure\\CLI2\\wbin" "C:\\Program Files (x86)\\Microsoft SDKs\\Azure\\CLI2\\wbin") do (
                        if exist "%%~p\\az.cmd" if "!AZ_EXE_FOUND!"=="" set "AZ_EXE_FOUND=%%~p\\az.cmd"
                    )
                    if "!AZ_EXE_FOUND!"=="" (
                        where az >nul 2>&1
                        if !ERRORLEVEL! equ 0 set "AZ_EXE_FOUND=az"
                    )
                    if "!AZ_EXE_FOUND!" neq "" echo !AZ_EXE_FOUND!
                    exit /b 0
                    """).trim()
                    }

                    withCredentials([
                        usernamePassword(credentialsId: 'aws-creds', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY'),
                        file(credentialsId: 'gcp-key', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
                        usernamePassword(credentialsId: 'azure-creds', usernameVariable: 'ARM_CLIENT_ID', passwordVariable: 'ARM_CLIENT_SECRET')
                    ]) {
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
                } else if (params.ACTION == 'DEPLOY') {
                    body += "Instance ID: ${env.TARGET_INSTANCE_ID}\n"
                    body += "Private IP: ${env.PRIVATE_IP}\n"
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
                if (fileExists('private_key.pem')) { isUnix() ? sh("rm -f private_key.pem") : bat("del private_key.pem") }
                if (fileExists('private_key.pem.pub')) { isUnix() ? sh("rm -f private_key.pem.pub") : bat("del private_key.pem.pub") }
            }
            archiveArtifacts artifacts: 'manifest.json', allowEmptyArchive: true
        }
    }
}

def buildImage() {
    def keyName = "prod-key-${BUILD_NUMBER}"
    env.KEY_NAME = keyName
    def privateKeyFile = isUnix() ? "${WORKSPACE}/private_key.pem" : "${WORKSPACE}\\private_key.pem"
    def osType = params.IMAGE_TYPE.toLowerCase()
    def packerSource = ""

    // 1. Generate and Store SSH Key
    stage("Setup and Store Key for ${params.CLOUD}") {
        if (isUnix()) { sh "ssh-keygen -t rsa -b 2048 -f private_key.pem -N \"\"" } else { bat "ssh-keygen -t rsa -b 2048 -f private_key.pem -N \"\"" }
        switch(params.CLOUD) {
            case 'AWS':
                packerSource = "amazon-ebs.aws_${osType}"
                if (isUnix()) {
                    sh """
                    aws ec2 import-key-pair --key-name ${keyName} --public-key-material fileb://private_key.pem.pub --region ${params.AWS_REGION}
                    aws secretsmanager create-secret --name ${keyName}-secret --secret-string file://private_key.pem --region ${params.AWS_REGION}
                    """
                } else {
                    bat """
                    call aws ec2 import-key-pair --key-name ${keyName} --public-key-material fileb://private_key.pem.pub --region ${params.AWS_REGION}
                    call aws secretsmanager create-secret --name ${keyName}-secret --secret-string file://private_key.pem --region ${params.AWS_REGION}
                    """
                }
                break
            case 'GCP':
                packerSource = "googlecompute.gcp_${osType}"
                if (isUnix()) {
                    sh """
                    echo "Authenticating gcloud with service account..."
                    ${env.GCLOUD_EXE} auth activate-service-account --key-file="\${GOOGLE_APPLICATION_CREDENTIALS}" --quiet
                    echo "Enabling Secret Manager API..."
                    ${env.GCLOUD_EXE} services enable secretmanager.googleapis.com --project=${params.GCP_PROJECT} --quiet
                    ${env.GCLOUD_EXE} secrets create ${keyName}-secret --replication-policy="automatic" --project=${params.GCP_PROJECT} --quiet 2>/dev/null || true
                    ${env.GCLOUD_EXE} secrets versions add ${keyName}-secret --data-file=private_key.pem --project=${params.GCP_PROJECT} --quiet
                    """
                } else {
                    bat """
                    @echo off
                    set GCLOUD_EXE=${env.GCLOUD_EXE}
                    echo Authenticating gcloud with service account...
                    call "%GCLOUD_EXE%" auth activate-service-account --key-file="%GOOGLE_APPLICATION_CREDENTIALS%" --quiet
                    echo Enabling Secret Manager API...
                    call "%GCLOUD_EXE%" services enable secretmanager.googleapis.com --project=${params.GCP_PROJECT} --quiet
                    call "%GCLOUD_EXE%" secrets create ${keyName}-secret --replication-policy="automatic" --project=${params.GCP_PROJECT} --quiet 2>nul
                    call "%GCLOUD_EXE%" secrets versions add ${keyName}-secret --data-file=private_key.pem --project=${params.GCP_PROJECT} --quiet
                    """
                }
                break
            case 'AZURE':
                packerSource = "azure-arm.azure_${osType}"
                if (isUnix()) {
                    sh """
                    ${env.AZ_EXE} login --service-principal -u \${ARM_CLIENT_ID} -p \${ARM_CLIENT_SECRET} --tenant ${params.AZURE_TENANT_ID} >/dev/null 2>&1
                    ${env.AZ_EXE} account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                    
                    if ! ${env.AZ_EXE} group show --name ${params.AZURE_RESOURCE_GROUP} >/dev/null 2>&1; then
                        echo "Creating Resource Group: ${params.AZURE_RESOURCE_GROUP}"
                        ${env.AZ_EXE} group create --name ${params.AZURE_RESOURCE_GROUP} --location "${params.AZURE_LOCATION}"
                    fi

                    if ! ${env.AZ_EXE} keyvault show --name ${params.AZURE_VAULT_NAME} >/dev/null 2>&1; then
                        echo "Creating Key Vault: ${params.AZURE_VAULT_NAME}"
                        ${env.AZ_EXE} keyvault create --name ${params.AZURE_VAULT_NAME} --resource-group ${params.AZURE_RESOURCE_GROUP} --location "${params.AZURE_LOCATION}" --enable-rbac-authorization true
                        sleep 30
                    fi

                    if ! ${env.AZ_EXE} keyvault secret set --vault-name ${params.AZURE_VAULT_NAME} --name "test-permission" --value "test" >/dev/null 2>&1; then
                        echo "[INFO] Service Principal lacks permission. Attempting to grant 'Key Vault Secrets Officer' role..."
                        ${env.AZ_EXE} role assignment create --role "Key Vault Secrets Officer" --assignee \${ARM_CLIENT_ID} --scope "/subscriptions/${params.AZURE_SUBSCRIPTION_ID}/resourceGroups/${params.AZURE_RESOURCE_GROUP}/providers/Microsoft.KeyVault/vaults/${params.AZURE_VAULT_NAME}" || { echo "Role assignment failed."; exit 1; }
                        sleep 60
                    fi
                    
                    ${env.AZ_EXE} keyvault secret set --vault-name ${params.AZURE_VAULT_NAME} --name ${keyName}-secret --file private_key.pem
                    """
                } else {
                bat """
                @echo off
                setlocal enabledelayedexpansion
                set "PYTHONHOME="
                set "PYTHONPATH="
                set "PYTHONEXECUTABLE="
                set AZ_EXE=${env.AZ_EXE}
                
                call "!AZ_EXE!" login --service-principal -u %ARM_CLIENT_ID% -p %ARM_CLIENT_SECRET% --tenant ${params.AZURE_TENANT_ID}
                call "!AZ_EXE!" account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                
                :: Ensure Resource Group exists
                call "!AZ_EXE!" group show --name ${params.AZURE_RESOURCE_GROUP} >nul 2>&1
                if !ERRORLEVEL! neq 0 (
                    echo Creating Resource Group: ${params.AZURE_RESOURCE_GROUP}
                    call "!AZ_EXE!" group create --name ${params.AZURE_RESOURCE_GROUP} --location ${params.AZURE_LOCATION}
                )

                :: Ensure Vault exists
                call "!AZ_EXE!" keyvault show --name ${params.AZURE_VAULT_NAME} >nul 2>&1
                if !ERRORLEVEL! neq 0 (
                    echo Creating Key Vault: ${params.AZURE_VAULT_NAME}
                    call "!AZ_EXE!" keyvault create --name ${params.AZURE_VAULT_NAME} --resource-group ${params.AZURE_RESOURCE_GROUP} --location ${params.AZURE_LOCATION} --enable-rbac-authorization true
                    if !ERRORLEVEL! neq 0 (
                        echo ERROR: Failed to create Key Vault '${params.AZURE_VAULT_NAME}'.
                        exit /b 1
                    )
                    echo Waiting 30 seconds for DNS propagation...
                    ping 127.0.0.1 -n 31 > nul
                )

                :: Handle Permissions
                call "!AZ_EXE!" keyvault secret set --vault-name ${params.AZURE_VAULT_NAME} --name "test-permission" --value "test" >nul 2>&1
                if !ERRORLEVEL! neq 0 (
                    echo [INFO] Service Principal lacks permission. Attempting to grant 'Key Vault Secrets Officer' role...
                    call "!AZ_EXE!" role assignment create --role "Key Vault Secrets Officer" --assignee %ARM_CLIENT_ID% --scope "/subscriptions/${params.AZURE_SUBSCRIPTION_ID}/resourceGroups/${params.AZURE_RESOURCE_GROUP}/providers/Microsoft.KeyVault/vaults/${params.AZURE_VAULT_NAME}"
                    if !ERRORLEVEL! equ 0 (
                        echo [SUCCESS] Permission granted. Waiting 60s for propagation...
                        ping 127.0.0.1 -n 61 > nul
                    ) else (
                        echo [ERROR] Role assignment failed. Your Service Principal must have 'Owner' or 'Role Based Access Control Administrator' permissions to grant itself Key Vault access.
                        exit /b 1
                    )
                )
                
                call "!AZ_EXE!" keyvault secret set --vault-name ${params.AZURE_VAULT_NAME} --name ${keyName}-secret --file private_key.pem
                """
                }
                break
        }
    }

    // 2. Run Packer Build
    stage("Build Image for ${params.CLOUD}") {
        try {
            // Only set PKR_VARs if Jenkins parameters are provided (allows dev.pkrvars.hcl to act as default)
            if (params.AWS_COPY_REGIONS) env.PKR_VAR_aws_ami_regions = "[\"${params.AWS_COPY_REGIONS.split(',').collect{it.trim()}.join('\",\"')}\"]"
            if (params.AWS_SHARE_ACCOUNTS) env.PKR_VAR_aws_ami_users = "[\"${params.AWS_SHARE_ACCOUNTS.split(',').collect{it.trim()}.join('\",\"')}\"]"
            if (params.GCP_STORAGE_LOCATIONS) env.PKR_VAR_gcp_storage_locations = "[\"${params.GCP_STORAGE_LOCATIONS.split(',').collect{it.trim()}.join('\",\"')}\"]"
            if (params.AZURE_GALLERY_RG) env.PKR_VAR_azure_gallery_rg = params.AZURE_GALLERY_RG
            if (params.AZURE_GALLERY_NAME) env.PKR_VAR_azure_gallery_name = params.AZURE_GALLERY_NAME
            if (params.AZURE_GALLERY_REGIONS) env.PKR_VAR_azure_gallery_regions = "[\"${params.AZURE_GALLERY_REGIONS.split(',').collect{it.trim()}.join('\",\"')}\"]"

            def packerCmd = isUnix() ? "packer" : "${PACKER_EXE}"
            if (isUnix()) {
                sh """
                if ! command -v packer &> /dev/null; then
                    echo "Packer not found. Downloading dynamically..."
                    curl -fsSL https://releases.hashicorp.com/packer/1.10.2/packer_1.10.2_linux_amd64.zip -o packer.zip
                    unzip -q -o packer.zip
                    chmod +x packer
                fi
                export PATH="\$PWD:\$PATH"
                ${packerCmd} init .
                ${packerCmd} validate -
                ${packerCmd} build \\
                    -only=${packerSource} \\
                    -var-file="dev.pkrvars.hcl" \\
                    -var "image_name=${params.IMAGE_NAME}" \\
                    -var "image_type=${params.IMAGE_TYPE}" \\
                    -var "region=${params.AWS_REGION}" \\
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
            } else {
                bat """
                ${PACKER_EXE} init .
                ${PACKER_EXE} validate -
                ${PACKER_EXE} build ^
                    -only=${packerSource} ^
                    -var-file="dev.pkrvars.hcl" ^
                    -var "image_name=${params.IMAGE_NAME}" ^
                    -var "image_type=${params.IMAGE_TYPE}" ^
                    -var "region=${params.AWS_REGION}" ^
                    -var "aws_key_name=${keyName}" ^
                    -var "aws_private_key_file=${privateKeyFile}" ^
                    -var "gcp_project=${params.GCP_PROJECT}" ^
                    -var "gcp_zone=${params.GCP_ZONE}" ^
                    -var "azure_resource_group=${params.AZURE_RESOURCE_GROUP}" ^
                    -var "azure_location=${params.AZURE_LOCATION}" ^
                    -var "azure_subscription_id=${params.AZURE_SUBSCRIPTION_ID}" ^
                    -var "azure_tenant_id=${params.AZURE_TENANT_ID}" ^
                    -var "azure_client_id=%ARM_CLIENT_ID%" ^
                    -var "azure_client_secret=%ARM_CLIENT_SECRET%" ^
                    ${PACKER_TEMPLATE}
                """
            }
        } finally {
            // This ensures the key is deleted even if the build fails
            if (fileExists('private_key.pem')) { isUnix() ? sh("rm -f private_key.pem") : bat("del private_key.pem") }
            if (fileExists('private_key.pem.pub')) { isUnix() ? sh("rm -f private_key.pem.pub") : bat("del private_key.pem.pub") }
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
        if (params.IMAGE_ID == '') {
            error "IMAGE_ID parameter is required for DEPLOY action. Please provide the AMI ID, GCP Image Name, or Azure Image ID."
        }
        if (params.SECRET_NAME == '') {
            error "SECRET_NAME parameter is required for DEPLOY action. Please provide the name of the secret holding the SSH key."
        }

        if (params.CLOUD == 'AWS') {
            def mongoEval = ""
            switch(params.MONGO_OP) {
                case 'CREATE':
                    mongoEval = "db.createCollection('${params.MONGO_COLLECTION}');\nprint('Collection created.');"
                    break
                case 'INSERT':
                    mongoEval = "db.${params.MONGO_COLLECTION}.insertOne(${params.MONGO_DOCUMENT});\nprint('Document inserted.');"
                    break
                case 'UPDATE':
                    mongoEval = "db.${params.MONGO_COLLECTION}.updateMany(${params.MONGO_DOCUMENT});\nprint('Documents updated.');"
                    break
                case 'DELETE':
                    mongoEval = "db.${params.MONGO_COLLECTION}.deleteMany(${params.MONGO_DOCUMENT});\nprint('Documents deleted.');"
                    break
            }

                // Retrieve MongoDB Credentials from AWS Secrets Manager
                if (isUnix()) {
                    sh """
                    export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
                    export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
                    aws secretsmanager get-secret-value --secret-id mongodb-creds --query SecretString --output text --region ${params.AWS_REGION} > mongo_creds.json
                    """
                } else {
                    bat """@echo off
                    set AWS_ACCESS_KEY_ID=%AWS_ACCESS_KEY_ID%
                    set AWS_SECRET_ACCESS_KEY=%AWS_SECRET_ACCESS_KEY%
                    aws secretsmanager get-secret-value --secret-id mongodb-creds --query SecretString --output text --region ${params.AWS_REGION} > mongo_creds.json
                    """
                }
                def mongoCreds = readJSON file: 'mongo_creds.json'
                def mongoUser = mongoCreds.username
                def mongoPass = mongoCreds.password
                if (isUnix()) { sh "rm -f mongo_creds.json" } else { bat "del mongo_creds.json" }

            writeFile file: 'setup_mongo.sh', text: """#!/bin/bash
sudo apt-get update
sudo apt-get install -y gnupg curl
curl -fsSL https://www.mongodb.org/static/pgp/server-7.0.asc | sudo gpg -o /usr/share/keyrings/mongodb-server-7.0.gpg --dearmor --yes
echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y mongodb-org

sudo systemctl start mongod
sudo systemctl enable mongod

echo "Waiting for MongoDB to start..."
for i in {1..30}; do
    if mongosh --eval "db.adminCommand({ping: 1})" --quiet >/dev/null 2>&1; then
        break
    fi
    if [ \$i -eq 30 ]; then echo "Timeout waiting for MongoDB." && exit 1; fi
    sleep 2
done

echo "Creating admin user..."
mongosh admin --eval 'db.createUser({user: "${mongoUser}", pwd: "${mongoPass}", roles: [{role: "root", db: "admin"}]})'

echo "Enabling authentication..."
sudo sed -i 's/^#security:/security:\\n  authorization: enabled/' /etc/mongod.conf

sudo systemctl restart mongod
echo "Waiting for MongoDB to restart with auth..."
for i in {1..30}; do
    if mongosh admin -u '${mongoUser}' -p '${mongoPass}' --eval "db.adminCommand({ping: 1})" --quiet >/dev/null 2>&1; then
        break
    fi
    if [ \$i -eq 30 ]; then echo "Timeout waiting for authenticated MongoDB." && exit 1; fi
    sleep 2
done

cat << 'EOF' | mongosh admin -u '${mongoUser}' -p '${mongoPass}' --quiet
use devdb;
${mongoEval}
EOF
"""
        }
        switch(params.CLOUD) {
            case 'AWS':
                if (isUnix()) {
                    sh """
                    #!/bin/bash
                    export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
                    export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
                    
                    SG_ID=\$(aws ec2 describe-security-groups --group-names production-web-sg --query "SecurityGroups[0].GroupId" --output text --region ${params.AWS_REGION} 2>/dev/null)
                    if [ -z "\$SG_ID" ] || [ "\$SG_ID" == "None" ]; then
                        SG_ID=\$(aws ec2 create-security-group --group-name production-web-sg --description "Production Web SG" --query "GroupId" --output text --region ${params.AWS_REGION})
                    fi
                    aws ec2 authorize-security-group-ingress --group-id "\$SG_ID" --protocol tcp --port 22 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>/dev/null || true
                    aws ec2 authorize-security-group-ingress --group-id "\$SG_ID" --protocol tcp --port 80 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>/dev/null || true
                    aws ec2 authorize-security-group-ingress --group-id "\$SG_ID" --protocol icmp --port -1 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>/dev/null || true

                    KEY_NAME="${params.SECRET_NAME.replace('-secret','')}"
                    INST_ID=\$(aws ec2 run-instances --image-id ${params.IMAGE_ID} --instance-type t3.micro --security-group-ids "\$SG_ID" --key-name "\$KEY_NAME" --no-associate-public-ip-address --query "Instances[0].InstanceId" --output text --region ${params.AWS_REGION})
                    if [ -z "\$INST_ID" ]; then echo "ERROR: Failed to launch instance."; exit 1; fi
                    
                    aws ec2 wait instance-running --instance-ids "\$INST_ID" --region ${params.AWS_REGION}
                    PRIVATE_IP=\$(aws ec2 describe-instances --instance-ids "\$INST_ID" --query "Reservations[0].Instances[0].PrivateIpAddress" --output text --region ${params.AWS_REGION})
                    if [ -z "\$PRIVATE_IP" ]; then echo "ERROR: Failed to retrieve Private IP."; exit 1; fi
                    
                    echo "TARGET_INSTANCE_ID=\$INST_ID" > env.props
                    echo "PRIVATE_IP=\$PRIVATE_IP" >> env.props
                    
                    echo "Retrieving SSH key from Secrets Manager..."
                    aws secretsmanager get-secret-value --secret-id ${params.SECRET_NAME} --query SecretString --output text --region ${params.AWS_REGION} > private_key.pem
                    chmod 400 private_key.pem
                    
                    echo "Waiting 60 seconds for SSH service to become available..."
                    sleep 60
                    
                    echo "Uploading and executing MongoDB setup script via Bastion..."
                    scp -i private_key.pem -o StrictHostKeyChecking=no -o "ProxyCommand=ssh -i private_key.pem -o StrictHostKeyChecking=no -W %h:%p ubuntu@${params.BASTION_IP}" setup_mongo.sh ubuntu@\$PRIVATE_IP:/tmp/setup_mongo.sh
                    ssh -i private_key.pem -o StrictHostKeyChecking=no -o "ProxyCommand=ssh -i private_key.pem -o StrictHostKeyChecking=no -W %h:%p ubuntu@${params.BASTION_IP}" ubuntu@\$PRIVATE_IP "chmod +x /tmp/setup_mongo.sh && /tmp/setup_mongo.sh"
                    
                    rm -f private_key.pem
                    """
                } else {
                bat """
                @echo off
                setlocal enabledelayedexpansion
                set SG_ID=
                for /f "tokens=*" %%i in ('call aws ec2 describe-security-groups --group-names production-web-sg --query "SecurityGroups[0].GroupId" --output text --region ${params.AWS_REGION} 2^>nul') do set SG_ID=%%i
                if "!SG_ID!"=="" (
                    for /f "tokens=*" %%i in ('call aws ec2 create-security-group --group-name production-web-sg --description "Production Web SG" --query "GroupId" --output text --region ${params.AWS_REGION}') do set SG_ID=%%i
                    call aws ec2 authorize-security-group-ingress --group-id !SG_ID! --protocol tcp --port 22 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>nul
                    call aws ec2 authorize-security-group-ingress --group-id !SG_ID! --protocol tcp --port 80 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>nul
                    call aws ec2 authorize-security-group-ingress --group-id !SG_ID! --protocol icmp --port -1 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>nul
                )
                
                set KEY_NAME=${params.SECRET_NAME.replace('-secret','')}
                for /f "tokens=*" %%i in ('call aws ec2 run-instances --image-id ${params.IMAGE_ID} --instance-type t3.micro --security-group-ids !SG_ID! --key-name !KEY_NAME! --no-associate-public-ip-address --query "Instances[0].InstanceId" --output text --region ${params.AWS_REGION}') do set INST_ID=%%i
                
                if "!INST_ID!"=="" (
                    echo ERROR: Failed to launch instance. Check AWS CLI output.
                    exit /b 1
                )
                
                call aws ec2 wait instance-running --instance-ids !INST_ID! --region ${params.AWS_REGION}
                for /f "tokens=*" %%i in ('call aws ec2 describe-instances --instance-ids !INST_ID! --query "Reservations[0].Instances[0].PrivateIpAddress" --output text --region ${params.AWS_REGION}') do set PRIVATE_IP=%%i
                
                if "!PRIVATE_IP!"=="" (
                    echo ERROR: Failed to retrieve Private IP.
                    exit /b 1
                )
                
                echo TARGET_INSTANCE_ID=!INST_ID! > env.props
                echo PRIVATE_IP=!PRIVATE_IP! >> env.props
                
                :: Retrieve SSH key from AWS Secrets Manager
                echo Retrieving SSH key from Secrets Manager...
                call aws secretsmanager get-secret-value --secret-id ${params.SECRET_NAME} --query SecretString --output text --region ${params.AWS_REGION} > private_key.pem
                
                :: Fix SSH key permissions for Windows
                icacls private_key.pem /inheritance:r /Q
                for /f "tokens=*" %%a in ('whoami') do icacls private_key.pem /grant:r "%%a:(R)" /Q
                
                echo Waiting 60 seconds for SSH service to become available...
                ping 127.0.0.1 -n 61 > nul
                
                echo Uploading and executing MongoDB setup script via Bastion...
                scp -i private_key.pem -o StrictHostKeyChecking=no -o "ProxyCommand=ssh -i private_key.pem -o StrictHostKeyChecking=no -W %%h:%%p ubuntu@${params.BASTION_IP}" setup_mongo.sh ubuntu@!PRIVATE_IP!:/tmp/setup_mongo.sh
                ssh -i private_key.pem -o StrictHostKeyChecking=no -o "ProxyCommand=ssh -i private_key.pem -o StrictHostKeyChecking=no -W %%h:%%p ubuntu@${params.BASTION_IP}" ubuntu@!PRIVATE_IP! "chmod +x /tmp/setup_mongo.sh && /tmp/setup_mongo.sh"
                
                :: Cleanup local key
                if exist private_key.pem del private_key.pem
                """
                }
                break
            case 'GCP':
                if (isUnix()) {
                    sh """
                    #!/bin/bash
                    INSTANCE_NAME="prod-vm-${BUILD_NUMBER}"
                    ${env.GCLOUD_EXE} compute firewall-rules create allow-ssh-http-icmp --allow tcp:22,tcp:80,icmp --target-tags=prod-web --project=${params.GCP_PROJECT} 2>/dev/null || true
                    
                    ${env.GCLOUD_EXE} compute instances create \${INSTANCE_NAME} \\
                        --image=${params.IMAGE_ID} \\
                        --project=${params.GCP_PROJECT} \\
                        --zone=${params.GCP_ZONE} \\
                        --machine-type=e2-micro \\
                        --tags=prod-web \\
                        --no-address \\
                        --format="get(networkInterfaces[0].networkIP)" > private_ip.txt || { echo "GCP Instance launch failed."; exit 1; }
                    
                    PRIVATE_IP=\$(cat private_ip.txt)
                    
                    echo "TARGET_INSTANCE_ID=\${INSTANCE_NAME}" > env.props
                    echo "PRIVATE_IP=\${PRIVATE_IP}" >> env.props
                    """
                } else {
                bat """
                @echo off
                setlocal enabledelayedexpansion
                set GCLOUD_EXE=${env.GCLOUD_EXE}
                set INSTANCE_NAME=prod-vm-${BUILD_NUMBER}
                call "!GCLOUD_EXE!" compute firewall-rules create allow-ssh-http-icmp --allow tcp:22,tcp:80,icmp --target-tags=prod-web --project=${params.GCP_PROJECT} 2>nul
                
                call "!GCLOUD_EXE!" compute instances create %INSTANCE_NAME% ^
                    --image=${params.IMAGE_ID} ^
                    --project=${params.GCP_PROJECT} ^
                    --zone=${params.GCP_ZONE} ^
                    --machine-type=e2-micro ^
                    --tags=prod-web ^
                    --no-address ^
                    --format="get(networkInterfaces[0].networkIP)" > private_ip.txt
                
                if !ERRORLEVEL! neq 0 (
                    echo GCP Instance launch failed.
                    exit /b !ERRORLEVEL!
                )
                
                set /p PRIVATE_IP=<private_ip.txt
                echo TARGET_INSTANCE_ID=%INSTANCE_NAME% > env.props
                echo PRIVATE_IP=!PRIVATE_IP! >> env.props
                """
                }
                break
            case 'AZURE':
                if (isUnix()) {
                    sh """
                    #!/bin/bash
                    VM_NAME="prod-vm-${BUILD_NUMBER}"
                    
                    ${env.AZ_EXE} login --service-principal -u \${ARM_CLIENT_ID} -p \${ARM_CLIENT_SECRET} --tenant ${params.AZURE_TENANT_ID} >/dev/null 2>&1
                    ${env.AZ_EXE} account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                    
                    echo "Retrieving SSH key from Key Vault..."
                    ${env.AZ_EXE} keyvault secret show --vault-name ${params.AZURE_VAULT_NAME} --name ${params.SECRET_NAME} --query value -o tsv > private_key.pem || { echo "ERROR: Failed to retrieve secret."; exit 1; }
                    
                    chmod 400 private_key.pem
                    ssh-keygen -y -f private_key.pem > public_key.pub
                    
                    echo "Launching Azure VM..."
                    ${env.AZ_EXE} vm create \\
                        --resource-group ${params.AZURE_RESOURCE_GROUP} \\
                        --name \${VM_NAME} \\
                        --size Standard_B2ats_v2 \\
                        --image "${params.IMAGE_ID}" \\
                        --admin-username azureuser \\
                        --public-ip-address "" \\
                        --nsg-rule SSH \\
                        --ssh-key-values public_key.pub \\
                        --query "{ip:privateIps, id:id}" --output json > vm_info.json || { echo "Azure VM launch failed."; exit 1; }
                    
                    rm -f private_key.pem public_key.pub
                    ${env.AZ_EXE} vm open-port --resource-group ${params.AZURE_RESOURCE_GROUP} --name \${VM_NAME} --port 80
                    """
                } else {
                bat """
                @echo off
                setlocal enabledelayedexpansion
                set "PYTHONHOME="
                set "PYTHONPATH="
                set "PYTHONEXECUTABLE="
                set VM_NAME=prod-vm-${BUILD_NUMBER}
                set AZ_EXE=${env.AZ_EXE}
                
                call "!AZ_EXE!" login --service-principal -u %ARM_CLIENT_ID% -p %ARM_CLIENT_SECRET% --tenant ${params.AZURE_TENANT_ID}
                call "!AZ_EXE!" account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                
                :: Retrieve SSH key from Key Vault
                echo Retrieving SSH key from Key Vault...
                call "!AZ_EXE!" keyvault secret show --vault-name ${params.AZURE_VAULT_NAME} --name ${params.SECRET_NAME} --query value -o tsv > private_key.pem
                
                if !ERRORLEVEL! neq 0 (
                    echo ERROR: Failed to retrieve secret. Verify AZURE_VAULT_NAME and SECRET_NAME.
                    exit /b 1
                )
                
                :: Fix SSH key permissions on Windows
                icacls private_key.pem /inheritance:r /Q
                for /f "tokens=*" %%a in ('whoami') do icacls private_key.pem /grant:r "%%a:(R)" /Q
                
                :: Generate public key from private key
                ssh-keygen -y -f private_key.pem > public_key.pub
                
                echo Launching Azure VM...
                call "!AZ_EXE!" vm create ^
                    --resource-group ${params.AZURE_RESOURCE_GROUP} ^
                    --name %VM_NAME% ^
                    --size Standard_B2ats_v2 ^
                    --image "${params.IMAGE_ID}" ^
                    --admin-username azureuser ^
                    --public-ip-address "" ^
                    --nsg-rule SSH ^
                    --ssh-key-values public_key.pub ^
                    --query "{ip:privateIps, id:id}" --output json > vm_info.json

                set VM_EXIT_CODE=!ERRORLEVEL!
                
                :: Cleanup local keys
                if exist private_key.pem del private_key.pem
                if exist public_key.pub del public_key.pub
                
                if !VM_EXIT_CODE! neq 0 (
                    echo Azure VM launch failed.
                    exit /b !VM_EXIT_CODE!
                )

                call "!AZ_EXE!" vm open-port --resource-group ${params.AZURE_RESOURCE_GROUP} --name %VM_NAME% --port 80
                """
                }
                def vmInfo = readJSON file: 'vm_info.json'
                env.TARGET_INSTANCE_ID = vmInfo.id
                env.PRIVATE_IP = vmInfo.ip
                return // Skip property reading for Azure
        }
        def props = readProperties file: 'env.props'
        env.TARGET_INSTANCE_ID = props['TARGET_INSTANCE_ID']
        env.PRIVATE_IP = props['PRIVATE_IP']
    }
}

def stopInstance() {
    stage("Stop Instance on ${params.CLOUD}") {
        if (params.INSTANCE_ID == '') {
            error "INSTANCE_ID parameter is required for STOP action."
        }
        switch(params.CLOUD) {
            case 'AWS':
                if (isUnix()) {
                    sh "aws ec2 stop-instances --instance-ids ${params.INSTANCE_ID} --region ${params.AWS_REGION}"
                } else {
                    bat "call aws ec2 stop-instances --instance-ids ${params.INSTANCE_ID} --region ${params.AWS_REGION}"
                }
                break
            case 'GCP':
                if (isUnix()) {
                    sh "${env.GCLOUD_EXE} compute instances stop ${params.INSTANCE_ID} --project=${params.GCP_PROJECT} --zone=${params.GCP_ZONE}"
                } else {
                    bat """
                    @echo off
                    setlocal enabledelayedexpansion
                    set GCLOUD_EXE=${env.GCLOUD_EXE}
                    call "!GCLOUD_EXE!" compute instances stop ${params.INSTANCE_ID} --project=${params.GCP_PROJECT} --zone=${params.GCP_ZONE}
                    """
                }
                break
            case 'AZURE':
                 if (isUnix()) {
                     sh """
                     ${env.AZ_EXE} login --service-principal -u \${ARM_CLIENT_ID} -p \${ARM_CLIENT_SECRET} --tenant ${params.AZURE_TENANT_ID} >/dev/null 2>&1
                     ${env.AZ_EXE} account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                     ${env.AZ_EXE} vm deallocate --resource-group ${params.AZURE_RESOURCE_GROUP} --name ${params.INSTANCE_ID}
                     """
                 } else {
                 bat """
                    @echo off
                    setlocal enabledelayedexpansion
                    set "PYTHONHOME="
                    set "PYTHONPATH="
                    set "PYTHONEXECUTABLE="
                    set AZ_EXE=${env.AZ_EXE}
                    call "!AZ_EXE!" login --service-principal -u %ARM_CLIENT_ID% -p %ARM_CLIENT_SECRET% --tenant ${params.AZURE_TENANT_ID}
                    call "!AZ_EXE!" account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                    call "!AZ_EXE!" vm deallocate --resource-group ${params.AZURE_RESOURCE_GROUP} --name ${params.INSTANCE_ID}
                 """
                 }
                break
        }
        echo "Stop command issued for instance ${params.INSTANCE_ID} in ${params.CLOUD}."
    }
}
