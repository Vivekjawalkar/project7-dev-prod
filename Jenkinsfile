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
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Test') {
            steps {
                dir('app') {
                    sh 'mvn test'
                }
            }
        }

        stage('Docker Build') {
            steps {
                dir('app') {
                    sh """
                        docker build \
                        -t ${DEV_ECR}:${IMAGE_TAG} \
                        .
                    """
                }
            }
        }

        stage('Push Image to DEV ECR') {
            steps {
                sh """
                    aws ecr get-login-password --region ${AWS_REGION} | \
                    docker login --username AWS --password-stdin \
                    ${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com

                    docker push ${DEV_ECR}:${IMAGE_TAG}
                """
            }
        }

        stage('Deploy to DEV') {
            steps {
                sh """
                    cat > dev-task-definition-pipeline.json <<EOF
                    {
                      "family": "project7-dev-task",
                      "networkMode": "awsvpc",
                      "requiresCompatibilities": ["FARGATE"],
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
                    EOF

                    aws ecs register-task-definition \
                    --cli-input-json file://dev-task-definition-pipeline.json \
                    --region ${AWS_REGION}

                    aws ecs update-service \
                    --cluster ${DEV_CLUSTER} \
                    --service ${DEV_SERVICE} \
                    --task-definition project7-dev-task \
                    --region ${AWS_REGION}

                    aws ecs wait services-stable \
                    --cluster ${DEV_CLUSTER} \
                    --services ${DEV_SERVICE} \
                    --region ${AWS_REGION}
                """
            }
        }

        stage('Test DEV') {
            steps {
                sh """
                    ENI_ID=\$(aws ecs describe-tasks \
                      --cluster ${DEV_CLUSTER} \
                      --service-name ${DEV_SERVICE} \
                      --desired-status RUNNING \
                      --region ${AWS_REGION} \
                      --query 'tasks[0].attachments[0].details[?name==\\`networkInterfaceId\\`].value' \
                      --output text)

                    DEV_PUBLIC_IP=\$(aws ec2 describe-network-interfaces \
                      --network-interface-ids \$ENI_ID \
                      --region ${AWS_REGION} \
                      --query 'NetworkInterfaces[0].Association.PublicIp' \
                      --output text)

                    echo "DEV Public IP: \$DEV_PUBLIC_IP"

                    RESPONSE=\$(curl -s --max-time 30 http://\$DEV_PUBLIC_IP:8080)

                    echo "DEV Response:"
                    echo "\$RESPONSE"

                    echo "\$RESPONSE" | grep -q "Project 7 - DEV to PRODUCTION Deployment"
                """
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
                sh """
                    docker pull ${DEV_ECR}:${IMAGE_TAG}

                    docker tag \
                    ${DEV_ECR}:${IMAGE_TAG} \
                    ${PROD_ECR}:${IMAGE_TAG}

                    docker push ${PROD_ECR}:${IMAGE_TAG}
                """
            }
        }

        stage('Deploy to PRODUCTION') {
            steps {
                sh """
                    cat > prod-task-definition-pipeline.json <<EOF
                    {
                      "family": "project7-prod-task",
                      "networkMode": "awsvpc",
                      "requiresCompatibilities": ["FARGATE"],
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
                    EOF

                    aws ecs register-task-definition \
                    --cli-input-json file://prod-task-definition-pipeline.json \
                    --region ${AWS_REGION}

                    aws ecs update-service \
                    --cluster ${PROD_CLUSTER} \
                    --service ${PROD_SERVICE} \
                    --task-definition project7-prod-task \
                    --region ${AWS_REGION}

                    aws ecs wait services-stable \
                    --cluster ${PROD_CLUSTER} \
                    --services ${PROD_SERVICE} \
                    --region ${AWS_REGION}
                """
            }
        }

        stage('Verify PRODUCTION') {
            steps {
                sh """
                    aws ecs describe-services \
                    --cluster ${PROD_CLUSTER} \
                    --services ${PROD_SERVICE} \
                    --region ${AWS_REGION} \
                    --query 'services[0].[status,desiredCount,runningCount,taskDefinition]' \
                    --output table
                """
            }
        }
    }

    post {
        success {
            echo 'Project 7 DEV to PRODUCTION deployment completed successfully.'
        }

        failure {
            echo 'Project 7 deployment pipeline failed.'
        }
    }
}
