## CloudFormation Stack Files

These Stack files are located in the `cloudFormationStacks` directory and contain the following code. <br>

**1-vpc.yaml** contains the basic VPC layout, subnets, and internet gateway, etc. <br>
**2-rds.yaml** contains the rds instances, cloudfront CDN, the <br>
**3-repos-dns.yaml**  contains the ecr repository, the git repository, dns, <br>
**4-elastic-beanstalk.yaml** contains all the code pertaining to the ElasticBeanstalk and ECR deployment.<br>

These are meant do be deployed 

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

Additionally a `.dockerignore` file like the below would omit packages left over from npm that are not necessary when copying them into the container image. <br>
```dockerfile
node_modules
npm-debug.log
```

## Building Docker Image Pipeline

To use this template to create a new Jenkins job and select "Pipeline" as the job type. Then copy and paste the below code into the pipeline script section. <br>
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

1. Defines environment variables.
2. Clones the stack repo to a local directory
3. Defines the template base bath and template file names.
4. Installs the AWS CLI.
5. Authenticates the AWS CLI with the provided credentials.
6. Defines the variables for the CloudFormation template files, with stack name, template path, tags, and parameters.
7. Runs a for loop and uses the `aws cloudformation deploy` CLI command with the specified parameters to deploy all the stacks.
```groovy
pipeline {
  agent any

  environment {
    AWS_REGION = 'us-east-1'
  }

  stages {
    stage('Clone CloudFormation stack repo') {
      steps {
        git branch: 'main', url: 'ssh://git-codecommit.us-east-1.amazonaws.com/v1/repos/eavor-pipeline-repo'
      }
    }

    stage('Deploy CloudFormation Stacks') {
      environment {
        TEMPLATE_BASE_PATH = '~/jenkins/workspace/eavor-pipeline-repo/cloudFormationStacks'
        FILE1 = '1-vpc.yaml'
        FILE2 = '2-rds.yaml'
        FILE3 = '3-repos-dns.yaml'
        FILE4 = '4-elastic-beanstalk.yaml'
      }
      steps {
        sh 'pip install awscli --upgrade --user'
        withAWS(region: AWS_REGION, credentials: 'aws-creds') {
          def templateFiles = [
            [name: 'vpc-stack', path: "${TEMPLATE_BASE_PATH}/${FILE1}", tags: "name=vpc", params: ""],
            [name: 'rds-stack', path: "${TEMPLATE_BASE_PATH}/${FILE2}", tags: "name=rds", params: ""],
            [name: 'repo-dns-stack', path: "${TEMPLATE_BASE_PATH}/${FILE3}", tags: "name=reposdns", params: ""],
            [name: 'beanstalk-stack', path: "${TEMPLATE_BASE_PATH}/${FILE4}", tags: "name=beanstalk", params: ""],
          ]

          for (template in templateFiles) {
            sh "aws cloudformation deploy \
                --stack-name ${template.name} \
                --template-file ${template.path} \
                --tags ${template.tags} \
                --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
                ${template.params}"
          }
        }
      }
    }
  }
}
```

## Updating Elastic Beanstalk with the new ECR image

Ensure that you have the appropriate AWS credentials set up in Jenkins with permission to perform these actions, and also replace the environment variables with your own values.
The Jenkins pipeline script does the following:

1. Defines the environment variables for AWS Region, Elastic Beanstalk environment name, ECS cluster, ECS task definition, ECR repository name, AWS account ID, and the Docker image tag.
2. Installs the AWS CLI.
3. Authenticates the AWS CLI with the provided credentials.
4. Gets the latest ECR image URI with the specified Docker image tag.
5. Gets the Elastic Beanstalk environment URL.
6. Registers a new task definition with the updated ECR image URI.
7. Adds the new task definition to the ECS service.
8. Deploys a new version to Elastic Beanstalk with the updated task definition.
```groovy
pipeline {
  agent any

  environment {
    AWS_REGION = 'us-east-1'
    AWS_EB_ENV_NAME = 'eavor-ds-nodejs-beanstalk'
    AWS_ECS_CLUSTER = 'eavor-ds-nodejs-beanstalk'
    AWS_ECS_TASK_DEFINITION = 'eavor-ds-nodejs-app:version'
    AWS_ECR_REPO_NAME = 'eavor-ds-nodejs'
    AWS_ACCOUNT_ID = '123456789012'
    IMAGE_TAG = 'latest'
  }

  stages {
    stage('Deploy ECR image to Elastic Beanstalk') {
      steps {
        script {
          // Install AWS CLI
          sh 'pip install awscli --upgrade --user'

          // Authenticate AWS CLI
          withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-creds', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
            sh 'aws configure set region ' + AWS_REGION
          }

          // Get latest ECR image URI
          def ecrImageUri = sh(returnStdout: true, script: "aws ecr describe-images --region ${AWS_REGION} --repository-name ${AWS_ECR_REPO_NAME} --image-ids imageTag=${IMAGE_TAG} --query 'images[0].imageUri' --output text").trim()
          
          // Get Elastic Beanstalk environment URL
          def ebEnvUrl = sh(returnStdout: true, script: "aws elasticbeanstalk describe-environments --region ${AWS_REGION} --environment-names ${AWS_EB_ENV_NAME} --query 'Environments[0].CNAME' --output text").trim()
          
          // Register new task definition
          def newTaskDefinitionArn = sh(returnStdout: true, script: "aws ecs register-task-definition --region ${AWS_REGION} --execution-role-arn arn:aws:iam::${AWS_ACCOUNT_ID}:role/ecsTaskExecutionRole --family ${AWS_ECS_TASK_DEFINITION} --container-definitions '[{\"name\":\"my-container-name\",\"image\":\"${ecrImageUri}\",\"essential\":true}]' --query 'taskDefinition.taskDefinitionArn' --output text").trim()
          
          // Add new task definition to ECS service
          sh "aws ecs update-service --region ${AWS_REGION} --cluster ${AWS_ECS_CLUSTER} --service my-service-name --service ${AWS_ECS_CLUSTER} --task-definition ${newTaskDefinitionArn} --query 'service.taskDefinition'"

          // Deploy new version to Elastic Beanstalk
          sh "aws elasticbeanstalk create-application-version --region ${AWS_REGION} --application-name my-app-name --version-label v1 --source-bundle S3Bucket=my-s3-bucket-name,S3Key=my-app-package.zip"
          sh "aws elasticbeanstalk update-environment --region ${AWS_REGION} --environment-name ${AWS_EB_ENV_NAME} --version-label v1"
        }
      }
    }
  }
}

```
