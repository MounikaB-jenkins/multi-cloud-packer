def PACKER = 'C:\\DevopsProject\\packer.exe'

pipeline {
    agent any

    parameters {
        string(name: 'IMAGE_NAME', defaultValue: 'multi-cloud-ubuntu', description: 'Name for the Packer image')
        choice(name: 'IMAGE_TYPE', choices: ['Linux', 'Windows'], description: 'Operating system type')
        string(name: 'AWS_REGION', defaultValue: 'us-east-1', description: 'AWS region')
        string(name: 'AWS_KEY_NAME', defaultValue: 'my-aws-key', description: 'Existing AWS Key Pair name')
        string(name: 'GCP_ZONE', defaultValue: 'us-central1-a', description: 'GCP zone')
        string(name: 'AZURE_LOCATION', defaultValue: 'West Europe', description: 'Azure region')
        string(name: 'INSTANCE_TYPE', defaultValue: 't2.micro', description: 'AWS instance type')
        choice(name: 'INSTANCE_MODE', choices: ['General', 'Spot'], description: 'Instance type: General or Spot')
        booleanParam(name: 'DISABLE_PUBLIC_IP', defaultValue: false, description: 'Disable public IP assignment')
        string(name: 'GCP_PROJECT', defaultValue: 'packer-demo-456789', description: 'GCP project ID')
        string(name: 'AZURE_RESOURCE_GROUP', defaultValue: 'packer-resources', description: 'Azure resource group')
        string(name: 'AZURE_SUBSCRIPTION_ID', defaultValue: 'b943e408-73c1-4cea-b780-689120606f67', description: 'Azure subscription ID')
        string(name: 'AZURE_TENANT_ID', defaultValue: '8344e416-02b8-4b70-a912-1995cc408f19', description: 'Azure tenant ID')
        string(name: 'EMAIL', defaultValue: 'mounika.b5693@outlook.com', description: 'Email for instance status notification')
        booleanParam(name: 'BUILD_AWS', defaultValue: true, description: 'Build AWS AMI')
        booleanParam(name: 'BUILD_GCP', defaultValue: true, description: 'Build GCP Image')
        booleanParam(name: 'BUILD_AZURE', defaultValue: true, description: 'Build Azure Image')
        booleanParam(name: 'DEPLOY_AWS', defaultValue: true, description: 'Deploy AWS EC2 Instance')
        booleanParam(name: 'DEPLOY_GCP', defaultValue: true, description: 'Deploy GCP VM')
        booleanParam(name: 'DEPLOY_AZURE', defaultValue: true, description: 'Deploy Azure VM')
    }

    environment {
        PACKER_TEMPLATE = 'aws-ubuntu.pkr.hcl'
        APP_NAME = 'multi-cloud-nginx'
        ENV = 'dev'
        OWNER = 'jenkins'
        AZURE_SUBSCRIPTION_ID = "${params.AZURE_SUBSCRIPTION_ID}"
        AZURE_TENANT_ID = "${params.AZURE_TENANT_ID}"
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                git branch: 'main',
                      credentialsId: 'github-token',
                      url: 'https://github.com/MounikaB-jenkins/multi-cloud-packer.git'
            }
        }

        stage('Init & Validate') {
            steps {
                bat """
                if exist "C:\\DevopsProject\\packer.exe" (
                    copy /Y "C:\\DevopsProject\\packer.exe" .\\packer.exe
                ) else (
                    echo ERROR: Packer executable not found at C:\\DevopsProject\\packer.exe
                    exit /b 1
                )
                packer.exe init .
                packer.exe validate ${PACKER_TEMPLATE}
                """
            }
        }

        stage('Setup AWS Key Pair') {
            when { expression { params.BUILD_AWS || params.DEPLOY_AWS } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'aws-creds', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    script {
                        env.AWS_KEY_NAME = "packer-${BUILD_NUMBER}"
                        bat """
                        setlocal enabledelayedexpansion
                        set AWS_ACCESS_KEY_ID=%AWS_ACCESS_KEY_ID%
                        set AWS_SECRET_ACCESS_KEY=%AWS_SECRET_ACCESS_KEY%
                        
                        echo Creating key-pair: %AWS_KEY_NAME%
                        aws ec2 create-key-pair --key-name ${env.AWS_KEY_NAME} --region ${params.AWS_REGION} --query "KeyMaterial" --output text > private_key.pem
                        if %ERRORLEVEL% neq 0 (
                            echo Failed to create key-pair
                            exit /b %ERRORLEVEL%
                        )

                        echo Storing key-pair in AWS Secrets Manager...
                        aws secretsmanager create-secret --name ${env.AWS_KEY_NAME}-secret --description "Packer build key-pair for build ${BUILD_NUMBER}" --secret-string file://private_key.pem --region ${params.AWS_REGION}
                        if %ERRORLEVEL% neq 0 (
                            echo Failed to store secret in Secrets Manager
                            del private_key.pem
                            exit /b %ERRORLEVEL%
                        )

                        echo Key-pair setup complete. Private key retained for build.
                        set AWS_PRIVATE_KEY_FILE=%cd%\\private_key.pem
                        echo AWS_PRIVATE_KEY_FILE=!AWS_PRIVATE_KEY_FILE!
                        """
                        env.AWS_PRIVATE_KEY_FILE = "${WORKSPACE}\\private_key.pem"
                    }
                }
            }
        }

        stage('Build Images') {
            steps {
                script {
                    try {
                        def vars = [
                            "-var \"image_name=${params.IMAGE_NAME}\"",
                            "-var \"region=${params.AWS_REGION}\"",
                            "-var \"aws_key_name=${env.AWS_KEY_NAME ?: params.AWS_KEY_NAME}\"",
                            "-var \"aws_private_key_file=${env.AWS_PRIVATE_KEY_FILE ?: ''}\"",
                            "-var \"gcp_project=${params.GCP_PROJECT}\"",
                            "-var \"gcp_zone=${params.GCP_ZONE}\"",
                            "-var \"azure_location=${params.AZURE_LOCATION}\"",
                            "-var \"azure_resource_group=${params.AZURE_RESOURCE_GROUP}\"",
                            "-var \"azure_subscription_id=${params.AZURE_SUBSCRIPTION_ID}\"",
                            "-var \"azure_tenant_id=${params.AZURE_TENANT_ID}\"",
                            "-var \"image_type=${params.IMAGE_TYPE}\"",
                            "-var \"instance_type=${params.INSTANCE_TYPE}\"",
                            "-var \"disable_public_ip=${params.DISABLE_PUBLIC_IP}\""
                        ].join(" ")

                        // Determine source names based on image type
                        def osType = params.IMAGE_TYPE == "Windows" ? "windows" : "linux"
                        def sources = []
                        if (params.BUILD_AWS) sources.add("amazon-ebs.aws_${osType}")
                        if (params.BUILD_GCP) sources.add("googlecompute.gcp_${osType}")
                        if (params.BUILD_AZURE) sources.add("azure-arm.azure_${osType}")

                        if (sources.size() == 0) {
                            error("No cloud providers selected for build.")
                        }

                        def onlyFlag = "--only=${sources.join(',')}"
                        echo "Packer only targets: ${onlyFlag}"

                        withCredentials([
                            usernamePassword(credentialsId: 'aws-creds', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY'),
                            file(credentialsId: 'gcp-key', variable: 'GOOGLE_APPLICATION_CREDENTIALS'),
                            usernamePassword(credentialsId: 'azure-creds', usernameVariable: 'ARM_CLIENT_ID', passwordVariable: 'ARM_CLIENT_SECRET')
                        ]) {
                            bat """
                            echo Copying Packer executable to workspace...
                            if exist "C:\\DevopsProject\\packer.exe" (
                                copy /Y "C:\\DevopsProject\\packer.exe" .\\packer.exe
                            ) else (
                                echo ERROR: Packer executable not found at C:\\DevopsProject\\packer.exe
                                exit /b 1
                            )
                            setlocal enabledelayedexpansion
                            set AWS_ACCESS_KEY_ID=%AWS_ACCESS_KEY_ID%
                            set AWS_SECRET_ACCESS_KEY=%AWS_SECRET_ACCESS_KEY%
                            set GOOGLE_APPLICATION_CREDENTIALS=%GOOGLE_APPLICATION_CREDENTIALS%
                            set ARM_CLIENT_ID=%ARM_CLIENT_ID%
                            set ARM_CLIENT_SECRET=%ARM_CLIENT_SECRET%
                            set ARM_SUBSCRIPTION_ID=${params.AZURE_SUBSCRIPTION_ID}
                            set ARM_TENANT_ID=${params.AZURE_TENANT_ID}

                            echo Current workspace: %cd%
                            dir /b

                            echo Testing Packer template...
                            if exist "${PACKER_TEMPLATE}" (
                                echo Packer template found
                            ) else (
                                echo Packer template not found: ${PACKER_TEMPLATE}
                                dir /b
                                exit /b 1
                            )
                            echo Testing Packer executable...
                            if exist packer.exe (
                                echo Packer executable found
                            ) else (
                                echo Packer executable not found
                                exit /b 1
                            )
                            echo Testing Packer executable...
                            packer.exe --version
                            if %ERRORLEVEL% neq 0 (
                                echo Packer executable test failed
                                exit /b %ERRORLEVEL%
                            )

                            echo Running Packer build...
                            echo Command: packer.exe build ${onlyFlag} ${vars} -var "azure_client_id=%ARM_CLIENT_ID%" -var "azure_client_secret=%ARM_CLIENT_SECRET%" ${PACKER_TEMPLATE}
                            echo Environment variables:
                            echo ARM_CLIENT_ID=%ARM_CLIENT_ID%
                            echo ARM_SUBSCRIPTION_ID=%ARM_SUBSCRIPTION_ID%
                            echo ARM_TENANT_ID=%ARM_TENANT_ID%
                            packer.exe build ${onlyFlag} ${vars} -var "azure_client_id=%ARM_CLIENT_ID%" -var "azure_client_secret=%ARM_CLIENT_SECRET%" ${PACKER_TEMPLATE}
                            if %ERRORLEVEL% neq 0 (
                                echo Packer build failed with exit code %ERRORLEVEL%
                                exit /b %ERRORLEVEL%
                            )

                            echo Listing workspace after Packer build...
                            dir /b
                            dir /s manifest.json || echo manifest.json not found in workspace tree

                            if exist "manifest.json" (
                                echo manifest.json created
                            ) else (
                                echo manifest.json missing after build
                                exit /b 1
                            )
                            """

                            stash includes: 'manifest.json', name: 'packer-manifest'
                        }
                    } finally {
                        if (env.AWS_PRIVATE_KEY_FILE && fileExists(env.AWS_PRIVATE_KEY_FILE)) {
                            echo "Cleaning up local private key..."
                            bat "del \"${env.AWS_PRIVATE_KEY_FILE}\""
                        }
                    }
                }
            }
        }

        stage('Extract Image IDs') {
            steps {
                script {
                    unstash 'packer-manifest'
                    bat 'echo Current workspace: %cd% && dir /b'
                    try {
                        if (!fileExists('manifest.json')) {
                            error('manifest.json not found; build likely failed before artifact creation.')
                        }

                        def manifestContent = readFile('manifest.json').trim()
                        def manifest = readJSON text: manifestContent
                        
                        echo "=== Build Artifacts ==="
                        if (manifest.builds) {
                            manifest.builds.each { build ->
                                echo "${build.name}: ${build.artifact_id}"
                            }
                        }

                        manifest.builds?.each { build ->
                            if (build.name.contains("aws")) {
                                env.AMI_ID = build.artifact_id?.split(":")[1]
                                echo "✓ AWS AMI ID: ${env.AMI_ID}"
                            } else if (build.name.contains("gcp")) {
                                env.GCP_IMAGE = build.artifact_id?.split("/")?.last()
                                echo "✓ GCP Image: ${env.GCP_IMAGE}"
                            } else if (build.name.contains("azure")) {
                                env.AZURE_IMAGE = build.artifact_id
                                echo "✓ Azure Image: ${env.AZURE_IMAGE}"
                            }
                        }
                    } catch (Exception e) {
                        echo "Warning: Could not parse manifest - ${e.message}"
                        error('Manifest extraction failed, stopping pipeline to avoid skipped deploy stages.')
                    }
                }
            }
        }

        stage('Deploy Instances') {
            when { expression { params.DEPLOY_AWS || params.DEPLOY_GCP || params.DEPLOY_AZURE } }
            parallel {
                stage('Deploy AWS EC2') {
                    when { expression { params.DEPLOY_AWS && env.AMI_ID } }
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'aws-creds', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                            bat """
                            setlocal enabledelayedexpansion
                            set AWS_ACCESS_KEY_ID=%AWS_ACCESS_KEY_ID%
                            set AWS_SECRET_ACCESS_KEY=%AWS_SECRET_ACCESS_KEY%
                            
                            set SPOT_FLAG=
                            if "${params.INSTANCE_MODE}"=="Spot" set SPOT_FLAG=--instance-market-options MarketType=spot
                            
                            set NO_PIP=
                            if "${params.DISABLE_PUBLIC_IP}"=="true" set NO_PIP=--no-associate-public-ip-address
                            
                            aws ec2 run-instances ^
                              --image-id ${env.AMI_ID} ^
                              --instance-type ${params.INSTANCE_TYPE} ^
                              --region ${params.AWS_REGION} ^
                              --key-name ${env.AWS_KEY_NAME ?: params.AWS_KEY_NAME} ^
                              !SPOT_FLAG! ^
                              !NO_PIP! ^
                              --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${APP_NAME}},{Key=Environment,Value=${ENV}}]"
                            """
                        }
                    }
                }

                stage('Deploy GCP VM') {
                    when { expression { params.DEPLOY_GCP && env.GCP_IMAGE } }
                    steps {
                        withCredentials([file(credentialsId: 'gcp-key', variable: 'GOOGLE_APPLICATION_CREDENTIALS')]) {
                            bat """
                            setlocal enabledelayedexpansion
                            set GOOGLE_APPLICATION_CREDENTIALS=%GOOGLE_APPLICATION_CREDENTIALS%
                            
                            gcloud compute instances create ${APP_NAME}-gcp ^
                              --image=${env.GCP_IMAGE} ^
                              --image-project=${params.GCP_PROJECT} ^
                              --zone=${params.GCP_ZONE} ^
                              --machine-type=e2-micro ^
                              --no-address ^
                              --labels=app=${APP_NAME},env=${ENV}
                            """
                        }
                    }
                }

                stage('Deploy Azure VM') {
                    when { expression { params.DEPLOY_AZURE && env.AZURE_IMAGE } }
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'azure-creds', usernameVariable: 'ARM_CLIENT_ID', passwordVariable: 'ARM_CLIENT_SECRET')]) {
                            bat """
                            setlocal enabledelayedexpansion
                            set ARM_CLIENT_ID=%ARM_CLIENT_ID%
                            set ARM_CLIENT_SECRET=%ARM_CLIENT_SECRET%
                            set ARM_SUBSCRIPTION_ID=${params.AZURE_SUBSCRIPTION_ID}
                            set ARM_TENANT_ID=${params.AZURE_TENANT_ID}
                            
                            az login --service-principal -u !ARM_CLIENT_ID! -p !ARM_CLIENT_SECRET! --tenant ${params.AZURE_TENANT_ID}
                            az account set --subscription ${params.AZURE_SUBSCRIPTION_ID}
                            
                            az vm create ^
                              --name ${APP_NAME}-azure ^
                              --image ${env.AZURE_IMAGE} ^
                              --resource-group ${params.AZURE_RESOURCE_GROUP} ^
                              --location "${params.AZURE_LOCATION}" ^
                              --size Standard_B2ats_v2 ^
                              --tags app=${APP_NAME} env=${ENV}
                            """
                        }
                    }
                }
            }
        }

        stage('Send Notification') {
            steps {
                script {
                    def status = currentBuild.result == 'SUCCESS' ? '✅ SUCCESS' : '❌ FAILED'
                    def emailBody = """
                    Multi-Cloud Packer Build & Deployment Report
                    ================================================
                    
                    Build Status: ${status}
                    Build Number: ${BUILD_NUMBER}
                    Image Type: ${params.IMAGE_TYPE}
                    Instance Mode: ${params.INSTANCE_MODE}
                    Public IP: ${params.DISABLE_PUBLIC_IP ? 'Disabled' : 'Enabled'}
                    
                    Build Artifacts:
                    - AWS AMI: ${env.AMI_ID ?: 'Failed'}
                    - GCP Image: ${env.GCP_IMAGE ?: 'Failed'}
                    - Azure Image: ${env.AZURE_IMAGE ?: 'Failed'}
                    
                    Jenkins Job: ${BUILD_URL}
                    
                    This is an automated notification.
                    """
                    
                    emailext(
                        subject: "Multi-Cloud Packer Report - Build ${BUILD_NUMBER}",
                        body: emailBody,
                        to: "${params.EMAIL}",
                        mimeType: 'text/plain'
                    )
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'manifest.json', allowEmptyArchive: true, fingerprint: true
        }
    }
}
