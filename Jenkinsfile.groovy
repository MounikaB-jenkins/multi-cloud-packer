pipeline {
    agent any

    parameters {
        choice(name: 'ACTION', choices: ['BUILD', 'DEPLOY', 'STOP'], description: 'The action to perform: BUILD a new image, DEPLOY an instance from an image, or STOP a running instance.')
        choice(name: 'CLOUD', choices: ['AWS', 'GCP', 'AZURE'], description: 'The target cloud provider.')
        
        string(name: 'BRANCH_NAME', defaultValue: 'development', description: 'Git branch to checkout and run (e.g., development, main, feature/xyz).')
        // Build Parameters
        string(name: 'IMAGE_NAME', defaultValue: 'multi-cloud-image', description: 'Name for the Packer image (used for BUILD action).')
        choice(name: 'IMAGE_TYPE', choices: ['Linux', 'Windows'], description: 'Operating system type (used for BUILD action).')

        // Deploy/Stop Parameters
        string(name: 'IMAGE_ID', defaultValue: '', description: 'The Image ID to launch (AMI ID, GCP Image Name, Azure Image ID). Required for DEPLOY action.')
        string(name: 'INSTANCE_ID', defaultValue: '', description: 'The ID of the instance to stop. Required for STOP action.')
        string(name: 'SECRET_NAME', defaultValue: '', description: 'The name of the secret holding the SSH key (e.g., prod-key-1-secret). Required for DEPLOY action.')

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
                if (fileExists('private_key.pem')) { bat "del private_key.pem" }
                if (fileExists('private_key.pem.pub')) { bat "del private_key.pem.pub" }
            }
            archiveArtifacts artifacts: 'manifest.json', allowEmptyArchive: true
        }
    }
}

def buildImage() {
    def keyName = "prod-key-${BUILD_NUMBER}"
    env.KEY_NAME = keyName
    def privateKeyFile = "${WORKSPACE}\\private_key.pem"
    def osType = params.IMAGE_TYPE.toLowerCase()
    def packerSource = ""

    // 1. Generate and Store SSH Key
    stage("Setup and Store Key for ${params.CLOUD}") {
        bat "ssh-keygen -t rsa -b 2048 -f private_key.pem -N \"\""
        switch(params.CLOUD) {
            case 'AWS':
                packerSource = "amazon-ebs.aws_${osType}"
                bat """
                call aws ec2 import-key-pair --key-name ${keyName} --public-key-material fileb://private_key.pem.pub --region ${params.AWS_REGION}
                call aws secretsmanager create-secret --name ${keyName}-secret --secret-string file://private_key.pem --region ${params.AWS_REGION}
                """
                break
            case 'GCP':
                packerSource = "googlecompute.gcp_${osType}"
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
                break
            case 'AZURE':
                packerSource = "azure-arm.azure_${osType}"
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
        } finally {
            // This ensures the key is deleted even if the build fails
            if (fileExists('private_key.pem')) { bat "del private_key.pem" }
            if (fileExists('private_key.pem.pub')) { bat "del private_key.pem.pub" }
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
                bat """
                set SG_ID=
                for /f "tokens=*" %%i in ('call aws ec2 describe-security-groups --group-names production-web-sg --query "SecurityGroups[0].GroupId" --output text --region ${params.AWS_REGION} 2^>nul') do set SG_ID=%%i
                if "%SG_ID%"=="" (
                    for /f "tokens=*" %%i in ('call aws ec2 create-security-group --group-name production-web-sg --description "Production Web SG" --query "GroupId" --output text --region ${params.AWS_REGION}') do set SG_ID=%%i
                    call aws ec2 authorize-security-group-ingress --group-id %SG_ID% --protocol tcp --port 22 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>nul
                    call aws ec2 authorize-security-group-ingress --group-id %SG_ID% --protocol tcp --port 80 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>nul
                    call aws ec2 authorize-security-group-ingress --group-id %SG_ID% --protocol icmp --port -1 --cidr 0.0.0.0/0 --region ${params.AWS_REGION} 2>nul
                )
                
                set KEY_NAME=${params.SECRET_NAME.replace('-secret','')}
                for /f "tokens=*" %%i in ('call aws ec2 run-instances --image-id ${params.IMAGE_ID} --instance-type t2.micro --security-group-ids %SG_ID% --key-name %KEY_NAME% --query "Instances[0].InstanceId" --output text --region ${params.AWS_REGION}') do set INST_ID=%%i
                call aws ec2 wait instance-running --instance-ids %INST_ID% --region ${params.AWS_REGION}
                for /f "tokens=*" %%i in ('call aws ec2 describe-instances --instance-ids %INST_ID% --query "Reservations[0].Instances[0].PublicIpAddress" --output text --region ${params.AWS_REGION}') do set PUBLIC_IP=%%i
                
                echo TARGET_INSTANCE_ID=%INST_ID% > env.props
                echo PUBLIC_IP=%PUBLIC_IP% >> env.props
                """
                break
            case 'GCP':
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
                    --format="get(networkInterfaces[0].accessConfigs[0].natIP)" > nat_ip.txt
                
                if !ERRORLEVEL! neq 0 (
                    echo GCP Instance launch failed.
                    exit /b !ERRORLEVEL!
                )
                
                set /p PUBLIC_IP=<nat_ip.txt
                echo TARGET_INSTANCE_ID=%INSTANCE_NAME% > env.props
                echo PUBLIC_IP=!PUBLIC_IP! >> env.props
                """
                break
            case 'AZURE':
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
                icacls private_key.pem /grant:r "%USERNAME%:(R)" /Q
                
                :: Generate public key from private key
                ssh-keygen -y -f private_key.pem > public_key.pub
                
                echo Launching Azure VM...
                call "!AZ_EXE!" vm create ^
                    --resource-group ${params.AZURE_RESOURCE_GROUP} ^
                    --name %VM_NAME% ^
                    --size Standard_B2ats_v2 ^
                    --image "${params.IMAGE_ID}" ^
                    --admin-username azureuser ^
                    --public-ip-sku Standard ^
                    --nsg-rule SSH ^
                    --ssh-key-values public_key.pub ^
                    --query "{ip:publicIpAddress, id:id}" --output json > vm_info.json

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
                bat "call aws ec2 stop-instances --instance-ids ${params.INSTANCE_ID} --region ${params.AWS_REGION}"
                break
            case 'GCP':
                bat """
                @echo off
                setlocal enabledelayedexpansion
                set GCLOUD_EXE=${env.GCLOUD_EXE}
                call "!GCLOUD_EXE!" compute instances stop ${params.INSTANCE_ID} --project=${params.GCP_PROJECT} --zone=${params.GCP_ZONE}
                """
                break
            case 'AZURE':
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
                break
        }
        echo "Stop command issued for instance ${params.INSTANCE_ID} in ${params.CLOUD}."
    }
}
