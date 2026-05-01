def PACKER = '"C:\\Users\\vresh\\Desktop\\TrainingFiles_Srikanthmentor\\multi-cloud-packer\\packer.exe"'

pipeline {
    agent any

    parameters {
        string(name: 'IMAGE_NAME', defaultValue: 'multi-cloud-ubuntu', description: 'Name for the Packer image')
        string(name: 'AWS_REGION', defaultValue: 'us-east-1', description: 'AWS region for AMI')
        string(name: 'GCP_PROJECT', defaultValue: 'packer-demo-456789', description: 'GCP project ID')
        string(name: 'GCP_ZONE', defaultValue: 'us-central1-a', description: 'GCP zone')
        string(name: 'AZURE_LOCATION', defaultValue: 'East US', description: 'Azure region')
        string(name: 'AZURE_RESOURCE_GROUP', defaultValue: 'packer-resources', description: 'Azure resource group')
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
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                      credentialsId: 'github-token',
                      url: 'https://github.com/MounikaB-jenkins/multi-cloud-packer.git'
            }
        }

        stage('Init & Validate') {
            steps {
                bat "${PACKER} init ."
                bat "${PACKER} validate ${PACKER_TEMPLATE}"
            }
        }

        stage('Build Images') {
            steps {
                script {
                    def packerCmd = "${PACKER} build "
                    def vars = [
                        "-var 'image_name=${params.IMAGE_NAME}'",
                        "-var 'region=${params.AWS_REGION}'",
                        "-var 'gcp_project=${params.GCP_PROJECT}'",
                        "-var 'gcp_zone=${params.GCP_ZONE}'",
                        "-var 'azure_location=${params.AZURE_LOCATION}'",
                        "-var 'azure_resource_group=${params.AZURE_RESOURCE_GROUP}'"
                    ].join(" ")

                    def sources = []
                    if (params.BUILD_AWS) sources.add("source.amazon-ebs.aws")
                    if (params.BUILD_GCP) sources.add("source.googlecompute.gcp")
                    if (params.BUILD_AZURE) sources.add("source.azure-arm.azure")

                    if (sources.size() == 0) {
                        error("No cloud providers selected for build.")
                    }

                    def onlyFlag = "--only=${sources.join(',')}"

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'aws-creds',
                            usernameVariable: 'AWS_ACCESS_KEY_ID',
                            passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                        ),
                        file(
                            credentialsId: 'gcp-key',
                            variable: 'GOOGLE_APPLICATION_CREDENTIALS'
                        ),
                        usernamePassword(
                            credentialsId: 'azure-creds',
                            usernameVariable: 'ARM_CLIENT_ID',
                            passwordVariable: 'ARM_CLIENT_SECRET'
                        )
                    ]) {
                        bat """
                        set AWS_ACCESS_KEY_ID=%AWS_ACCESS_KEY_ID%
                        set AWS_SECRET_ACCESS_KEY=%AWS_SECRET_ACCESS_KEY%
                        set GOOGLE_APPLICATION_CREDENTIALS=%GOOGLE_APPLICATION_CREDENTIALS%
                        set ARM_CLIENT_ID=%ARM_CLIENT_ID%
                        set ARM_CLIENT_SECRET=%ARM_CLIENT_SECRET%
                        set ARM_SUBSCRIPTION_ID=${env.AZURE_SUBSCRIPTION_ID}
                        set ARM_TENANT_ID=${env.AZURE_TENANT_ID}

                        ${PACKER} build ${onlyFlag} ${vars} ${PACKER_TEMPLATE}
                        """
                    }
                }
            }
        }

        stage('Extract Image IDs') {
            steps {
                script {
                    def manifest = readJSON file: 'manifest.json'
                    echo "Manifest content: ${groovy.json.JsonOutput.toJson(manifest)}"

                    def awsArtifact = manifest.builds.find { it.name == "amazon-ebs.aws" }
                    def gcpArtifact = manifest.builds.find { it.name == "googlecompute.gcp" }
                    def azureArtifact = manifest.builds.find { it.name == "azure-arm.azure" }

                    env.AMI_ID = awsArtifact ? awsArtifact.artifact_id.split(":")[1] : null
                    env.GCP_IMAGE = gcpArtifact ? gcpArtifact.artifact_id.split("/").last() : null
                    env.AZURE_IMAGE = azureArtifact ? azureArtifact.artifact_id : null

                    echo "AWS AMI ID: ${env.AMI_ID}"
                    echo "GCP Image Name: ${env.GCP_IMAGE}"
                    echo "Azure Image Name: ${env.AZURE_IMAGE}"

                    if ((params.DEPLOY_AWS && !env.AMI_ID) ||
                        (params.DEPLOY_GCP && !env.GCP_IMAGE) ||
                        (params.DEPLOY_AZURE && !env.AZURE_IMAGE)) {
                        error("Failed to extract required image IDs.")
                    }
                }
            }
        }

        stage('Deploy Instances') {
            parallel {
                stage('Deploy AWS EC2') {
                    when { expression { params.DEPLOY_AWS && env.AMI_ID } }
                    steps {
                        withCredentials([
                            usernamePassword(
                                credentialsId: 'aws-creds',
                                usernameVariable: 'AWS_ACCESS_KEY_ID',
                                passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                            )
                        ]) {
                            bat """
                            set AWS_ACCESS_KEY_ID=%AWS_ACCESS_KEY_ID%
                            set AWS_SECRET_ACCESS_KEY=%AWS_SECRET_ACCESS_KEY%
                            aws ec2 run-instances ^
                              --image-id %AMI_ID% ^
                              --instance-type t2.micro ^
                              --region %AWS_REGION% ^
                              --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=%APP_NAME%},{Key=Environment,Value=%ENV%},{Key=Owner,Value=%OWNER%}]"
                            """
                        }
                    }
                }

                stage('Deploy GCP VM') {
                    when { expression { params.DEPLOY_GCP && env.GCP_IMAGE } }
                    steps {
                        withCredentials([
                            file(
                                credentialsId: 'gcp-key',
                                variable: 'GOOGLE_APPLICATION_CREDENTIALS'
                            )
                        ]) {
                            bat """
                            set GOOGLE_APPLICATION_CREDENTIALS=%GOOGLE_APPLICATION_CREDENTIALS%
                            gcloud compute instances create ${APP_NAME}-vm ^
                              --image=%GCP_IMAGE% ^
                              --image-project=%GCP_PROJECT% ^
                              --zone=%GCP_ZONE% ^
                              --machine-type=e2-micro ^
                              --labels=app=%APP_NAME%,env=%ENV%,owner=%OWNER%
                            """
                        }
                    }
                }

                stage('Deploy Azure VM') {
                    when { expression { params.DEPLOY_AZURE && env.AZURE_IMAGE } }
                    steps {
                        withCredentials([
                            usernamePassword(
                                credentialsId: 'azure-creds',
                                usernameVariable: 'ARM_CLIENT_ID',
                                passwordVariable: 'ARM_CLIENT_SECRET'
                            )
                        ]) {
                            bat """
                            set ARM_CLIENT_ID=%ARM_CLIENT_ID%
                            set ARM_CLIENT_SECRET=%ARM_CLIENT_SECRET%
                            set ARM_SUBSCRIPTION_ID=%AZURE_SUBSCRIPTION_ID%
                            set ARM_TENANT_ID=%AZURE_TENANT_ID%

                            az vm create ^
                              --name ${APP_NAME}-vm ^
                              --image %AZURE_IMAGE% ^
                              --resource-group %AZURE_RESOURCE_GROUP% ^
                              --location %AZURE_LOCATION% ^
                              --size Standard_B1s ^
                              --tags app=%APP_NAME% env=%ENV% owner=%OWNER%
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'manifest.json', fingerprint: true
        }
        success {
            echo "✅ Pipeline succeeded!"
        }
        failure {
            echo "❌ Pipeline failed!"
        }
    }
}
