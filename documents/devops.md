## Node.js Dockerfile

The following is a Dockerfile that would sit along side the Node.js code in the git repo. <br>
```dockerfile
FROM node:16

ENV PGHOST my-db-instance.cgcoia1j17xe.us-east-1.rds.amazonaws.com
ENV PGUSER pgsqladmin
ENV PGPASSWORD sup3rS3cureP4ssw0rd
ENV PGDATABASE eavor-node-app
ENV PGPORT 5432

WORKDIR /app

COPY package*.json .

RUN npm install

COPY . .

CMD ["npm", "start"]
```

Additionally a dockerignore file would omit some packages that are not necessary when building the container image. <br>
```dockerfile
node_modules
npm-debug.log
```

## Building Docker Image Pipeline

To use this template, create a new Jenkins job and select "Pipeline" as the job type. Then copy and paste the above code into the pipeline script section. <br>
You will also need to add the AWS credentials to Jenkins and configure the environment variables for `AWS_REGION`, `AWS_ACCOUNT_ID`, and `IMAGE_NAME`.<br>


This pipeline does the following:<br> 
1. Checks out source code from a git branch.
2. Authenticate with ECR using the AWS CLI using credentials stored in Jenkins.
3. Builds the Docker image using the CLI.
4. Tags the Docker image with both the build number and the "latest" tag.
5. Pushes the Docker images to the ECR repository using the CLI.<br>
```groovy
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
```

## Deploying CloudFormation 

You'll need to update the variables in the script with your own values for the CloudFormation stack. Additionally, don't forget to create an AWS credentials in Jenkins with permission to deploy CloudFormation stacks.

1. Defines the variables for the CloudFormation stack name, region, template URL, tags, and parameters.
2. Installs the AWS CLI.
3. Authenticaes the AWS CLI with the provided credentials.
4. Runs the `aws cloudformation deploy` command with the specified parameters, including the CloudFormation stack name, template URL, tags, and parameters.
```groovy
pipeline {
  agent any

  stages {
    stage('Copy CloudFormation template yaml to S3') {
      steps {
        script {

        }
      }
    }
    stage('Deploy CloudFormation Stack') {
      steps {
        script {
          def stackName = 'my-stack-name'
          def region = 'us-east-1'
          def templateUrl = 'https://s3.amazonaws.com/my-bucket-name/cloudformation.yaml'
          def tags = "Key=Value,OtherKey=OtherValue"

          // Install AWS CLI
          sh 'pip install awscli --upgrade --user'

          // Authenticate AWS CLI
          withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-creds', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
            sh 'aws configure set region ' + region
          }

          // Deploy CloudFormation Stack
          sh "aws cloudformation deploy \
              --stack-name $stackName \
              --template-url $templateUrl \
              --tags $tags \
              --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM"
        }
      }
    }
  }
}
```
