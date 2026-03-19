---
name: spring-boot-jenkins
description: 선언형(Declarative) Jenkinsfile을 작성하여 Spring Boot 애플리케이션의 소스 코드를 가져오고, 멀티스테이지 Dockerfile을 이용해 이미지를 빌드한 뒤, 컨테이너 레지스트리(Docker Hub, ECR 등)에 안전하게 푸시합니다.
argument-hint: "[사용할 레지스트리 주소, 크리덴셜 ID, 브랜치명 (예: main 브랜치 푸시 시 Docker Hub로 이미지를 빌드하고 푸시하는 Jenkinsfile 작성)]"
source: "custom"
tags: ["jenkins", "ci-cd", "devops", "docker", "pipeline", "infrastructure", "automation"]
triggers:
  - "Jenkinsfile 작성"
  - "젠킨스 파이프라인"
  - "CI/CD 구축"
---

# Spring Boot Jenkins CI/CD Pipeline

MSA 및 쿠버네티스(K8s) 배포를 위한 필수 전제 조건인 '컨테이너 이미지 자동 빌드 및 레지스트리 푸시(Push)' 과정을 선언형 파이프라인(Declarative Pipeline)으로 자동화하는 지침입니다.

## Overview

- **멀티스테이지 빌드 시너지:** 애플리케이션 빌드(`.jar` 생성)를 Jenkins가 아닌 `Dockerfile` 내부(Stage 1)로 위임합니다. Jenkins 서버는 오직 `docker build` 명령어만 실행하므로 서버 환경이 매우 깨끗하게 유지됩니다.
- **보안 중심:** 도커 허브(Docker Hub)나 AWS ECR 접속에 필요한 비밀번호/토큰을 코드에 하드코딩하지 않고, Jenkins의 Credentials 플러그인을 사용하여 안전하게 주입받습니다.
- **디스크 정리(Cleanup):** 빌드가 끝난 후 Jenkins 노드에 쌓이는 잉여 도커 이미지와 작업 공간(Workspace)을 깔끔하게 지워 디스크 용량 고갈(Disk Full) 장애를 원천 차단합니다.

## When to Use This Skill

- 개발자가 GitHub의 특정 브랜치(예: `main`, `develop`)에 코드를 푸시(Push)했을 때, 자동으로 도커 이미지가 생성되기를 원할 때.
- 완성된 도커 이미지를 쿠버네티스 클러스터가 가져다 쓸 수 있도록 원격 저장소(Registry)에 등록해야 할 때.
- 팀 내에 표준화된 CI(Continuous Integration) 프로세스를 도입하여 빌드 실패를 조기에 발견하고 싶을 때.

## How It Works

### Step 1: Environment & Credentials

파이프라인 상단 `environment` 블록에 이미지 이름, 태그(일반적으로 빌드 번호 지정), 레지스트리 URL을 정의합니다.
- `credentials()` 헬퍼 메서드를 사용하여 Jenkins에 미리 등록해 둔 인증 정보를 환경 변수로 안전하게 로드합니다.

### Step 2: Define Stages

`stages` 블록 내에 파이프라인의 흐름을 정의합니다.
- **Checkout:** Git 저장소에서 소스 코드를 내려받습니다.
- **Build Image:** 미리 작성된 멀티스테이지 `Dockerfile`을 실행하여 이미지를 굽습니다.
- **Push Image:** 원하지 않는 브랜치의 코드가 운영 레지스트리에 덮어씌워지는 것을 막기 위해 `when` 블록을 사용하여 특정 브랜치(예: `main`)일 때만 실행되도록 보호합니다.

### Step 3: Post Actions (Cleanup & Notify)

`post` 블록을 사용하여 빌드 성공/실패 여부와 관계없이 실행될 마무리 작업을 정의합니다.
- `always`: 워크스페이스 정리 및 로컬에 남은 도커 이미지 삭제를 수행합니다. (Jenkins 서버 생존의 핵심)
- `success` / `failure`: 슬랙(Slack)이나 이메일로 빌드 결과를 팀원들에게 알립니다.

## Examples

```groovy
// Jenkinsfile (Declarative Pipeline)
pipeline {
    // 아무 Jenkins 노드(Agent)에서나 실행되도록 허용
    agent any

    environment {
        // 도커 허브 계정명 또는 ECR 레지스트리 주소
        DOCKER_REGISTRY = 'my-docker-repo'
        IMAGE_NAME = 'spring-boot-msa-app'
        
        // 빌드 번호를 태그로 사용하여 이미지 버전을 고유하게 관리
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        FULL_IMAGE_NAME = "${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
        LATEST_IMAGE_NAME = "${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
        
        // Jenkins Credentials에 등록된 ID (예: 'docker-hub-credentials')
        // 자동으로 DOCKER_CREDS_USR, DOCKER_CREDS_PSW 환경 변수가 바인딩 됨
        DOCKER_CREDS = credentials('docker-hub-credentials')
    }

    stages {
        stage('1. Checkout Code') {
            steps {
                // SCM(Git)에서 소스 코드 체크아웃
                checkout scm
                echo "Checkout Complete. Start building ${FULL_IMAGE_NAME}"
            }
        }

        // 멀티스테이지 Dockerfile을 사용하므로 별도의 Gradle 빌드 스테이지가 불필요함
        stage('2. Build Docker Image') {
            steps {
                script {
                    echo "Building Docker Image using Multi-stage Dockerfile..."
                    // 도커 이미지 빌드
                    sh "docker build -t ${FULL_IMAGE_NAME} -t ${LATEST_IMAGE_NAME} ."
                }
            }
        }

        stage('3. Push to Registry') {
            // 방어 로직: main 브랜치일 때만 이미지를 원격에 푸시
            when {
                branch 'main'
            }
            steps {
                script {
                    echo "Logging into Docker Registry..."
                    // 보안: 비밀번호를 명령어에 직접 노출하지 않고 표준 입력으로 전달
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
                // 로컬 이미지 삭제 (Jenkins 디스크 고갈 방지, 실패해도 파이프라인이 멈추지 않도록 || true 사용)
                sh "docker rmi ${FULL_IMAGE_NAME} || true"
                sh "docker rmi ${LATEST_IMAGE_NAME} || true"
                
                // 불필요한 댕글링(dangling) 이미지 강제 정리
                sh "docker image prune -f"
            }
            // 작업 공간(Workspace) 비우기
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

* **멀티스테이지 도커파일 적극 활용:** 이 파이프라인의 가장 큰 장점은 Jenkins 서버에 JDK 21이나 Gradle을 설치할 필요가 없다는 것입니다. 빌드 로직은 전적으로 `Dockerfile`에 위임하여 Jenkins는 '오케스트레이터' 역할만 수행하게 하십시오.
* **버전(Tag) 관리 전략:** `latest` 태그만 덮어씌우면 문제 발생 시 롤백(Rollback)이 불가능합니다. 예제처럼 반드시 `${env.BUILD_NUMBER}`나 Git Commit Hash를 태그로 사용하여 이미지를 고유하게 식별(Versioning)하십시오.

## Common Pitfalls

* **하드코딩된 비밀번호 노출 (Critical):** `docker login -u user -p password` 형태로 파이프라인 스크립트를 작성하면 Jenkins 로그 파일과 Git 저장소에 비밀번호가 평문으로 남습니다. 반드시 Credentials 플러그인과 `password-stdin`을 사용하여 안전하게 주입하십시오.
* **디스크 고갈 (Disk Out of Space) (Critical):** Jenkins 서버 장애의 90%는 남은 도커 이미지와 빌드 캐시로 인한 디스크 용량 초과입니다. `post { always { ... } }` 블록에서 `docker rmi`와 `cleanWs()`를 누락하지 마십시오.
* **`env.GIT_COMMIT` 젠킨스 함정:** 커밋 해시를 이미지 태그로 쓰고 싶을 때, 상단 `environment` 블록에 `TAG = "${env.GIT_COMMIT}"`을 선언하면 값이 `null`로 나옵니다. 젠킨스 특성상 커밋 해시는 `checkout scm` 스테이지가 끝난 이후에만 채워지기 때문입니다. 커밋 해시를 쓰려면 체크아웃 이후의 `script` 블록에서 변수를 재할당해야 합니다.

## Related Skills

* `spring-boot-docker-build`: 이 파이프라인이 호출하여 빌드를 위임할 멀티스테이지 Dockerfile 작성 지침입니다.
* `spring-boot-k8s`: 레지스트리에 푸시된 이 이미지를 쿠버네티스 클러스터에 배포하기 위한 YAML 작성 지침입니다.
