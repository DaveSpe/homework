pipeline {
    agent any

    environment {
        AWS_REGION = 'us-east-1'
        AWS_ACCOUNT_ID = '123456789012'
        IMAGE_NAME = 'eavor-nodejs-prod'
    }

    stages {
        stage('Checkout source code') {
            steps {
                git branch: 'main', url: 'ssh://git-codecommit.us-east-1.amazonaws.com/v1/repos/eavor-nodejs-prod-app'
            }
        }
        stage('Authenticate with ECR') {
            steps {
                sh 'pip install awscli --upgrade --user'
                sh 'aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com'
            }
        }
        stage('Build Docker image') {
            steps {
                sh 'docker build -t $IMAGE_NAME:$BUILD_NUMBER .'
            }
        }

        stage('Tag Docker image') {
            steps {
                sh 'docker tag $IMAGE_NAME:$BUILD_NUMBER $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$IMAGE_NAME:$BUILD_NUMBER'
                sh 'docker tag $IMAGE_NAME:$BUILD_NUMBER $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$IMAGE_NAME:latest'
            }
        }

        stage('Push Docker image to ECR') {
            steps {
                sh 'docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$IMAGE_NAME:$BUILD_NUMBER'
                sh 'docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$IMAGE_NAME:latest'
            }
        }
    }
}

/*
This Jenkins job template defines a pipeline that does the following:

1. Checkout source code from the specified branch in the specified Git repository.

2. Authenticate with ECR using the AWS CLI and credentials stored in Jenkins.

3. Build the Docker image using the Docker CLI.

4. Tag the Docker image with the build number and "latest" tag.

5. Push the Docker image to the ECR repository using the Docker CLI.

To use this template, create a new Jenkins job and select "Pipeline" as the job type. Then copy and paste the above code into the pipeline script section. You will also need to add the AWS credentials to Jenkins and configure the environment variables for AWS_REGION, AWS_ACCOUNT_ID, and IMAGE_NAME.
*/
