---
name: spring-boot-jenkins
description: Write a Declarative Jenkinsfile to fetch Spring Boot source code, build a Docker image using a multi-stage Dockerfile, and securely push it to a container registry (Docker Hub, ECR, etc.).
argument-hint: "[사용할 레지스트리 주소, 크리덴셜 ID, 브랜치명 (예: main 브랜치 푸시 시 Docker Hub로 이미지를 빌드하고 푸시하는 Jenkinsfile 작성)]"
source: "custom"
tags: ["jenkins", "ci-cd", "devops", "docker", "pipeline", "infrastructure", "automation"]
triggers:
  - "Jenkinsfile 작성"
  - "젠킨스 파이프라인"
  - "CI/CD 구축"
---

# Spring Boot Jenkins CI/CD Pipeline

Guidelines for automating the "Container Image Build and Registry Push" process—an essential prerequisite for Microservices (MSA) and Kubernetes (K8s) deployments—using a Declarative Jenkins Pipeline.

## Overview

- **Multi-stage Build Synergy:** Delegates the application build (`.jar` generation) to the `Dockerfile` (Stage 1) instead of Jenkins. Because the Jenkins server only executes the `docker build` command, its environment remains extremely clean.
- **Security-First:** Never hardcodes passwords or tokens required for Docker Hub or AWS ECR. Securely injects them using the Jenkins Credentials plugin.
- **Disk Cleanup:** Cleanly removes surplus Docker images and the Workspace that accumulate on the Jenkins node after a build finishes, fundamentally preventing Disk Full outages.

## When to Use This Skill

- When you want a Docker image to be generated automatically upon code push to specific GitHub branches (e.g., `main`, `develop`).
- When the completed Docker image needs to be registered in a remote Registry so a Kubernetes cluster can pull it.
- When you want to introduce a standardized CI (Continuous Integration) process to your team to catch build failures early.

## How It Works

### Step 1: Environment & Credentials

Define the image name, tag (typically the build number), and registry URL in the `environment` block at the top of the pipeline.
- Use the `credentials()` helper method to securely load authentication info registered in Jenkins into environment variables.

### Step 2: Define Stages

Define the flow of the pipeline inside the `stages` block.
- **Checkout:** Downloads the source code from the Git repository.
- **Build Image:** Bakes the image by executing the pre-written multi-stage `Dockerfile`.
- **Push Image:** Uses a `when` block to ensure this stage only runs on specific branches (e.g., `main`) to prevent experimental code from overwriting production images in the registry.

### Step 3: Post Actions (Cleanup & Notify)

Use the `post` block to define final tasks that execute regardless of build success or failure.
- `always`: Cleans the workspace and deletes leftover local Docker images. (Crucial for Jenkins server survival).
- `success` / `failure`: Notifies the team of the build result via Slack or Email.

## Examples

```groovy
// Jenkinsfile (Declarative Pipeline)
pipeline {
    // Allow execution on any available Jenkins node (Agent)
    agent any

    environment {
        // Docker Hub account name or ECR registry address
        DOCKER_REGISTRY = 'my-docker-repo'
        IMAGE_NAME = 'spring-boot-msa-app'
        
        // Uniquely manage image versions using the build number as a tag
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        FULL_IMAGE_NAME = "${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
        LATEST_IMAGE_NAME = "${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
        
        // Registered ID in Jenkins Credentials (e.g., 'docker-hub-credentials')
        // This automatically binds DOCKER_CREDS_USR and DOCKER_CREDS_PSW env variables
        DOCKER_CREDS = credentials('docker-hub-credentials')
    }

    stages {
        stage('1. Checkout Code') {
            steps {
                // Checkout source code from SCM (Git)
                checkout scm
                echo "Checkout Complete. Start building ${FULL_IMAGE_NAME}"
            }
        }

        // No separate Gradle build stage is needed because we use a multi-stage Dockerfile!
        stage('2. Build Docker Image') {
            steps {
                script {
                    echo "Building Docker Image using Multi-stage Dockerfile..."
                    // Build the Docker image
                    sh "docker build -t ${FULL_IMAGE_NAME} -t ${LATEST_IMAGE_NAME} ."
                }
            }
        }

        stage('3. Push to Registry') {
            // Defensive logic: Only push the image to remote if on the main branch
            when {
                branch 'main'
            }
            steps {
                script {
                    echo "Logging into Docker Registry..."
                    // Security: Do not expose password directly in the command; pass it via stdin
                    sh "echo ${DOCKER_CREDS_PSW} | docker login -u ${DOCKER_CREDS_USR} --password-stdin"
                    
                    echo "Pushing Image..."
                    sh "docker push ${FULL_IMAGE_NAME}"
                    sh "docker push ${LATEST_IMAGE_NAME}"
                }
            }
        }
    }

    post {
        always {
            script {
                echo "Cleaning up local workspace and Docker images to free up space..."
                // Delete local images (Prevents Jenkins disk exhaustion; uses || true to prevent pipeline failure if image is absent)
                sh "docker rmi ${FULL_IMAGE_NAME} || true"
                sh "docker rmi ${LATEST_IMAGE_NAME} || true"
                
                // Force prune unnecessary dangling images
                sh "docker image prune -f"
            }
            // Clear the Workspace
            cleanWs()
        }
        success {
            echo "CI Pipeline Succeeded! Image is ready in the registry."
            // slackSend channel: '#deploy', message: "Build Success: ${env.JOB_NAME} [${env.BUILD_NUMBER}]"
        }
        failure {
            echo "CI Pipeline Failed. Please check the logs."
            // slackSend channel: '#deploy', message: "Build Failed: ${env.JOB_NAME} [${env.BUILD_NUMBER}]"
        }
    }
}

```

## Best Practices

* **Actively Utilize Multi-stage Dockerfiles:** The biggest advantage of this pipeline is that you do not need to install JDK 21 or Gradle on the Jenkins server. Delegate the build logic entirely to the `Dockerfile`, making Jenkins act solely as an 'orchestrator'.
* **Versioning (Tagging) Strategy:** If you only overwrite the `latest` tag, rolling back becomes impossible when issues arise. You MUST uniquely identify (version) images using tags like `${env.BUILD_NUMBER}` or a Git Commit Hash as shown in the example.

## Common Pitfalls

* **Exposing Hardcoded Passwords (Critical):** Writing pipeline scripts like `docker login -u user -p password` leaves the password in plain text in Jenkins log files and Git repositories. Always securely inject them using the Credentials plugin and `password-stdin`.
* **Disk Out of Space (Critical):** 90% of Jenkins server outages are caused by disk capacity exceeded due to leftover Docker images and build caches. Never omit `docker rmi` and `cleanWs()` in the `post { always { ... } }` block.
* **The `env.GIT_COMMIT` Jenkins Trap:** If you want to use the commit hash as the image tag and declare `TAG = "${env.GIT_COMMIT}"` in the top `environment` block, it will evaluate to `null`. Due to the nature of Jenkins, the commit hash is only populated *after* the `checkout scm` stage completes. To use it, you must reassign the variable within a `script` block after the checkout.

## Related Skills

* `spring-boot-docker-build`: The multi-stage Dockerfile creation guidelines that this pipeline will call to delegate the build.
* `spring-boot-k8s`: Guidelines for writing YAML manifests to deploy this image pushed to the registry onto a Kubernetes cluster.
