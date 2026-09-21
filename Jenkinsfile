cd ~/project7-dev-prod
cat > Jenkinsfile <<'EOF'
pipeline {
    agent any

    environment {
        AWS_REGION = 'us-east-1'
        AWS_ACCOUNT = '434504868934'

        DEV_CLUSTER = 'project7-dev-cluster'
        DEV_SERVICE = 'project7-dev-service'
        DEV_ECR = '434504868934.dkr.ecr.us-east-1.amazonaws.com/project7-dev-java-app'

        PROD_CLUSTER = 'project7-prod-cluster'
        PROD_SERVICE = 'project7-prod-service'
        PROD_ECR = '434504868934.dkr.ecr.us-east-1.amazonaws.com/project7-prod-java-app'

        EXECUTION_ROLE = 'arn:aws:iam::434504868934:role/ecsTaskExecutionRole'

        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                dir('app') {
                    sh '''
                        set -e

                        echo "Building Java application..."

                        mvn clean package -DskipTests

                        echo "Build completed successfully."
                    '''
                }
            }
        }

        stage('Test') {
            steps {
                dir('app') {
                    sh '''
                        set -e

                        echo "Running application tests..."

                        mvn test

                        echo "Application tests passed."
                    '''
                }
            }
        }

        stage('Docker Build') {
            steps {
                dir('app') {
                    sh '''
                        set -e

                        echo "Building Docker image..."

                        docker build \
                            -t ${DEV_ECR}:${IMAGE_TAG} \
                            .

                        echo "Docker image built successfully."

                        docker images | grep project7-dev-java-app
                    '''
                }
            }
        }

        stage('Push Image to DEV ECR') {
            steps {
                sh '''
                    set -e

                    echo "Logging in to DEV ECR..."

                    aws ecr get-login-password \
                        --region ${AWS_REGION} | \
                    docker login \
                        --username AWS \
                        --password-stdin ${DEV_ECR}

                    echo "Pushing image to DEV ECR..."

                    docker push ${DEV_ECR}:${IMAGE_TAG}

                    echo "DEV image pushed successfully."

                    aws ecr describe-images \
                        --repository-name project7-dev-java-app \
                        --image-ids imageTag=${IMAGE_TAG} \
                        --region ${AWS_REGION}
                '''
            }
        }

        stage('Deploy to DEV') {
            steps {
                sh '''
                    set -e

                    echo "Preparing DEV ECS task definition..."

                    cat > dev-task-definition-pipeline.json <<EOF_TASK
{
  "family": "project7-dev-task",
  "networkMode": "awsvpc",
  "requiresCompatibilities": [
    "FARGATE"
  ],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "${EXECUTION_ROLE}",
  "containerDefinitions": [
    {
      "name": "project7-dev-app",
      "image": "${DEV_ECR}:${IMAGE_TAG}",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/project7-dev-prod",
          "awslogs-region": "${AWS_REGION}",
          "awslogs-stream-prefix": "dev"
        }
      }
    }
  ]
}
EOF_TASK

                    echo "Registering DEV task definition..."

                    aws ecs register-task-definition \
                        --cli-input-json file://dev-task-definition-pipeline.json \
                        --region ${AWS_REGION}

                    echo "Updating DEV ECS service..."

                    aws ecs update-service \
                        --cluster ${DEV_CLUSTER} \
                        --service ${DEV_SERVICE} \
                        --task-definition project7-dev-task \
                        --force-new-deployment \
                        --region ${AWS_REGION}

                    echo "Waiting for DEV service to stabilize..."

                    aws ecs wait services-stable \
                        --cluster ${DEV_CLUSTER} \
                        --services ${DEV_SERVICE} \
                        --region ${AWS_REGION}

                    echo "DEV deployment completed successfully."
                '''
            }
        }

        stage('Test DEV') {
            steps {
                sh '''
                    set -e

                    echo "Finding running DEV task..."

                    TASK_ARN=$(aws ecs list-tasks \
                        --cluster ${DEV_CLUSTER} \
                        --service-name ${DEV_SERVICE} \
                        --desired-status RUNNING \
                        --region ${AWS_REGION} \
                        --query 'taskArns[0]' \
                        --output text)

                    echo "DEV Task ARN: ${TASK_ARN}"

                    if [ -z "${TASK_ARN}" ] || [ "${TASK_ARN}" = "None" ]; then
                        echo "ERROR: Could not find running DEV task."
                        exit 1
                    fi

                    echo "Finding DEV network interface..."

                    ENI_ID=$(aws ecs describe-tasks \
                        --cluster ${DEV_CLUSTER} \
                        --tasks ${TASK_ARN} \
                        --region ${AWS_REGION} \
                        --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
                        --output text)

                    echo "DEV ENI: ${ENI_ID}"

                    if [ -z "${ENI_ID}" ] || [ "${ENI_ID}" = "None" ]; then
                        echo "ERROR: Could not find DEV network interface."
                        exit 1
                    fi

                    echo "Finding DEV public IP..."

                    DEV_PUBLIC_IP=$(aws ec2 describe-network-interfaces \
                        --network-interface-ids ${ENI_ID} \
                        --region ${AWS_REGION} \
                        --query 'NetworkInterfaces[0].Association.PublicIp' \
                        --output text)

                    echo "DEV Public IP: ${DEV_PUBLIC_IP}"

                    if [ -z "${DEV_PUBLIC_IP}" ] || [ "${DEV_PUBLIC_IP}" = "None" ]; then
                        echo "ERROR: DEV task does not have a public IP."
                        exit 1
                    fi

                    echo "Testing DEV application..."

                    RESPONSE=$(curl -s \
                        --max-time 30 \
                        http://${DEV_PUBLIC_IP}:8080)

                    echo "DEV Response:"
                    echo "${RESPONSE}"

                    echo "${RESPONSE}" | grep -q "Project 7 - DEV to PRODUCTION Deployment"

                    echo "DEV application test PASSED."
                '''
            }
        }

        stage('Approval for PRODUCTION') {
            steps {
                input(
                    message: 'DEV testing passed. Approve this version for PRODUCTION?',
                    ok: 'Approve PRODUCTION Deployment'
                )
            }
        }

        stage('Promote Image to PROD ECR') {
            steps {
                sh '''
                    set -e

                    echo "Logging in to PROD ECR..."

                    aws ecr get-login-password \
                        --region ${AWS_REGION} | \
                    docker login \
                        --username AWS \
                        --password-stdin ${PROD_ECR}

                    echo "Pulling approved DEV image..."

                    docker pull ${DEV_ECR}:${IMAGE_TAG}

                    echo "Tagging approved image for PROD..."

                    docker tag \
                        ${DEV_ECR}:${IMAGE_TAG} \
                        ${PROD_ECR}:${IMAGE_TAG}

                    echo "Pushing approved image to PROD ECR..."

                    docker push ${PROD_ECR}:${IMAGE_TAG}

                    echo "PROD image promotion completed successfully."
                '''
            }
        }

        stage('Deploy to PRODUCTION') {
            steps {
                sh '''
                    set -e

                    echo "Preparing PRODUCTION ECS task definition..."

                    cat > prod-task-definition-pipeline.json <<EOF_TASK
{
  "family": "project7-prod-task",
  "networkMode": "awsvpc",
  "requiresCompatibilities": [
    "FARGATE"
  ],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "${EXECUTION_ROLE}",
  "containerDefinitions": [
    {
      "name": "project7-prod-app",
      "image": "${PROD_ECR}:${IMAGE_TAG}",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/project7-dev-prod",
          "awslogs-region": "${AWS_REGION}",
          "awslogs-stream-prefix": "prod"
        }
      }
    }
  ]
}
EOF_TASK

                    echo "Registering PRODUCTION task definition..."

                    aws ecs register-task-definition \
                        --cli-input-json file://prod-task-definition-pipeline.json \
                        --region ${AWS_REGION}

                    echo "Updating PRODUCTION ECS service..."

                    aws ecs update-service \
                        --cluster ${PROD_CLUSTER} \
                        --service ${PROD_SERVICE} \
                        --task-definition project7-prod-task \
                        --force-new-deployment \
                        --region ${AWS_REGION}

                    echo "Waiting for PRODUCTION service to stabilize..."

                    aws ecs wait services-stable \
                        --cluster ${PROD_CLUSTER} \
                        --services ${PROD_SERVICE} \
                        --region ${AWS_REGION}

                    echo "PRODUCTION deployment completed successfully."
                '''
            }
        }

        stage('Verify PRODUCTION') {
            steps {
                sh '''
                    set -e

                    echo "Checking PRODUCTION service..."

                    aws ecs describe-services \
                        --cluster ${PROD_CLUSTER} \
                        --services ${PROD_SERVICE} \
                        --region ${AWS_REGION} \
                        --query 'services[0].[status,desiredCount,runningCount,taskDefinition]' \
                        --output table

                    echo "Finding running PRODUCTION task..."

                    PROD_TASK_ARN=$(aws ecs list-tasks \
                        --cluster ${PROD_CLUSTER} \
                        --service-name ${PROD_SERVICE} \
                        --desired-status RUNNING \
                        --region ${AWS_REGION} \
                        --query 'taskArns[0]' \
                        --output text)

                    echo "PRODUCTION Task ARN: ${PROD_TASK_ARN}"

                    if [ -z "${PROD_TASK_ARN}" ] || [ "${PROD_TASK_ARN}" = "None" ]; then
                        echo "ERROR: No running PRODUCTION task found."
                        exit 1
                    fi

                    echo "PRODUCTION verification completed successfully."
                '''
            }
        }
    }

    post {
        success {
            echo 'Project 7 DEV to PRODUCTION deployment completed successfully.'
        }

        failure {
            echo 'Project 7 pipeline failed. Check the console output for details.'
        }

        always {
            echo 'Project 7 pipeline execution finished.'
        }
    }
}
EOF
