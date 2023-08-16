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
          sh 'pip install awscli --upgrade --user'

          withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-creds', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
            sh 'aws configure set region ' + AWS_REGION
          }

          def ecrImageUri = sh(returnStdout: true, script: "aws ecr describe-images --region ${AWS_REGION} --repository-name ${AWS_ECR_REPO_NAME} --image-ids imageTag=${IMAGE_TAG} --query 'images[0].imageUri' --output text").trim()

          def ebEnvUrl = sh(returnStdout: true, script: "aws elasticbeanstalk describe-environments --region ${AWS_REGION} --environment-names ${AWS_EB_ENV_NAME} --query 'Environments[0].CNAME' --output text").trim()

          def newTaskDefinitionArn = sh(returnStdout: true, script: "aws ecs register-task-definition --region ${AWS_REGION} --execution-role-arn arn:aws:iam::${AWS_ACCOUNT_ID}:role/ecsTaskExecutionRole --family ${AWS_ECS_TASK_DEFINITION} --container-definitions '[{\"name\":\"my-container-name\",\"image\":\"${ecrImageUri}\",\"essential\":true}]' --query 'taskDefinition.taskDefinitionArn' --output text").trim()

          sh "aws ecs update-service --region ${AWS_REGION} --cluster ${AWS_ECS_CLUSTER} --service my-service-name --service ${AWS_ECS_CLUSTER} --task-definition ${newTaskDefinitionArn} --query 'service.taskDefinition'"

          sh "aws elasticbeanstalk create-application-version --region ${AWS_REGION} --application-name my-app-name --version-label v1 --source-bundle S3Bucket=my-s3-bucket-name,S3Key=my-app-package.zip"
          sh "aws elasticbeanstalk update-environment --region ${AWS_REGION} --environment-name ${AWS_EB_ENV_NAME} --version-label v1"
        }
      }
    }
  }
}
