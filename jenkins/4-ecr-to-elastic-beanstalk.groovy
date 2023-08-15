Here is a Groovy template you can use in Jenkins to deploy an ECR image to Elastic Beanstalk:

```groovy
pipeline {
  agent any

  environment {
    AWS_REGION = 'us-east-1'
    
    AWS_EB_ENV_NAME = 'my-eb-environment-name'
    AWS_ECS_CLUSTER = 'my-ecs-cluster-name'
    AWS_ECS_TASK_DEFINITION = 'my-ecs-taskdef-family:my-ecs-taskdef-version'

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

/*
The script does the following:

1. Defines the environment variables for AWS Region, Elastic Beanstalk environment name, ECS cluster, ECS task definition, ECR repository name, AWS account ID, and the Docker image tag.

2. Installs the AWS CLI.

3. Authenticates the AWS CLI with the provided credentials.

4. Gets the latest ECR image URI with the specified Docker image tag.

5. Gets the Elastic Beanstalk environment URL.

6. Registers a new task definition with the updated ECR image URI.

7. Adds the new task definition to the ECS service.

8. Deploys a new version to Elastic Beanstalk with the updated task definition.

You'll need to ensure that you have the appropriate AWS credentials set up in Jenkins with permission to perform these actions, and also replace the environment variables with your own values.
*/
