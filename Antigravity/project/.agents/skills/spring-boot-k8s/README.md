---
name: spring-boot-k8s
description: Spring Boot 애플리케이션에 최적화된 쿠버네티스(Kubernetes) Deployment 및 Service YAML 매니페스트를 작성하여, 무중단 배포와 자원 최적화 환경을 구성합니다.
argument-hint: "[애플리케이션 이름, 도커 이미지 경로, 포트 번호, Spring Profile (예: my-app의 v1.0.0 이미지를 8080 포트로 배포하는 Deployment와 Service YAML을 작성)]"
source: "custom"
tags: ["kubernetes", "k8s", "spring-boot", "devops", "deployment", "msa", "infrastructure"]
triggers:
  - "쿠버네티스 배포"
  - "K8s YAML 작성"
---

# Spring Boot Kubernetes Manifests

도커(Docker) 컨테이너화된 Spring Boot 애플리케이션을 쿠버네티스 클러스터에 안정적으로 배포하고 서비스하기 위한 선언적 YAML 작성 지침입니다.

## Overview

- **Deployment (배포 및 관리):** 파드(Pod)의 개수(Replicas)를 유지하고 버전을 롤링 업데이트(Rolling Update)하는 역할을 합니다. Spring Boot의 상태를 K8s가 인지할 수 있도록 `Liveness Probe`와 `Readiness Probe`를 반드시 구성합니다.
- **Service (네트워크 노출):** 동적으로 생성/삭제되는 파드들의 IP를 단일 엔드포인트로 묶어 내부 또는 외부 트래픽을 라우팅합니다.
- **자원 격리 (Resource Quota):** 애플리케이션이 클러스터의 전체 메모리를 먹어 치우는 것을 방지하기 위해 `requests`와 `limits`를 엄격하게 설정합니다.

## When to Use This Skill

- `spring-boot-jenkins` 파이프라인을 통해 레지스트리에 푸시된 도커 이미지를 실제 운영/개발 K8s 클러스터에 띄워야 할 때.
- 트래픽 부하에 대비해 여러 대의 컨테이너(Pod)를 띄우고(Scale-out) 로드 밸런싱을 적용해야 할 때.
- 배포 중 서버가 다운되는 현상(Downtime)을 없애고 안전한 롤링 업데이트(무중단 배포) 아키텍처를 구성할 때.

## How It Works

### Step 1: Add Spring Boot Actuator

쿠버네티스가 애플리케이션의 상태를 정확히 파악하려면 Spring Boot 프로젝트(`build.gradle`)에 `spring-boot-starter-actuator` 의존성이 추가되어 있어야 합니다.
- `application.yml`에 K8s 전용 헬스 체크 그룹 노출 설정이 필요합니다. (`management.endpoint.health.probes.enabled=true`)

### Step 2: Define the `Service`

클러스터 내부에서 파드들과 통신하기 위한 네트워크 정책을 작성합니다.
- `selector`를 통해 트래픽을 보낼 파드의 라벨(`app: my-spring-app`)을 매핑합니다.
- 기본적으로 `type: ClusterIP`를 사용하여 클러스터 내부 통신망을 구성합니다. (외부 노출은 Ingress나 LoadBalancer를 별도로 사용)

### Step 3: Define the `Deployment`

애플리케이션 실행 명세서를 작성합니다.
- **Resources:** 컨테이너가 사용할 CPU와 Memory의 최소 보장량(`requests`)과 최대 한계치(`limits`)를 설정합니다.
- **Probes:** - `readinessProbe`: `/actuator/health/readiness`를 호출하여 200 OK가 떨어져야만 트래픽을 파드로 보냅니다. (무중단 배포의 핵심)
  - `livenessProbe`: `/actuator/health/liveness`를 호출하여 응답이 없으면 K8s가 파드를 죽이고 다시 살려냅니다. (자동 복구)
- **Lifecycle:** 애플리케이션 종료 시 처리 중인 요청이 끊기지 않도록 `preStop` 훅(Hook)을 설정할 수 있습니다.

## Examples

```yaml
# spring-boot-app.yaml
---
apiVersion: v1
kind: Service
metadata:
  name: my-spring-app-svc
  namespace: default
  labels:
    app: my-spring-app
spec:
  type: ClusterIP # 클러스터 내부 통신용 (외부 노출이 필요하면 LoadBalancer 사용)
  selector:
    app: my-spring-app
  ports:
    - name: http
      protocol: TCP
      port: 8080        # 서비스가 노출할 포트
      targetPort: 8080  # 컨테이너가 수신하는 포트

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app-deployment
  namespace: default
  labels:
    app: my-spring-app
spec:
  replicas: 2 # 기본 파드 2개 띄우기 (고가용성 확보)
  selector:
    matchLabels:
      app: my-spring-app
  strategy:
    type: RollingUpdate # 무중단 배포 전략
    rollingUpdate:
      maxSurge: 1       # 업데이트 시 추가로 띄울 수 있는 최대 파드 수
      maxUnavailable: 0 # 업데이트 중에도 최소 100%의 파드 유지
  template:
    metadata:
      labels:
        app: my-spring-app
    spec:
      containers:
        - name: my-spring-app-container
          # 주의: 운영 환경에서는 latest 사용을 피하고 명시적인 버전을 사용합니다.
          image: my-docker-repo/spring-boot-msa-app:v1.0.0
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          
          # 환경 변수 주입 (Spring Profile 등 설정)
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: TZ
              value: "Asia/Seoul"
              
          # 컨테이너 자원 할당 (OOM 방지)
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "500m"
              memory: "1024Mi"
              
          # Readiness Probe: 트래픽을 받을 준비가 되었는가? (무중단 배포 핵심)
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 15 # 앱 구동 대기 시간
            periodSeconds: 10
            failureThreshold: 3
            
          # Liveness Probe: 앱이 살아있는가? (응답 없으면 파드 재시작)
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 20
            failureThreshold: 3
            
          # Lifecycle: Graceful Shutdown을 위해 SIGTERM 신호 전 약간의 대기 시간 부여
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 10"]

```

## Best Practices

* **Spring Boot Graceful Shutdown 연동:** 무중단 배포를 완벽하게 하려면 쿠버네티스의 `preStop` 대기 시간과 함께, Spring Boot의 `application.yml`에 `server.shutdown=graceful` 설정을 추가하여 기존에 처리 중이던 HTTP 요청이 완료될 때까지 애플리케이션이 기다리도록 만들어야 합니다.
* **Actuator 포트 분리 (보안 강화):** 퍼블릭 트래픽을 받는 8080 포트에 `/actuator` 엔드포인트를 노출하는 것은 보안상 위험합니다. `management.server.port=8081`과 같이 분리하고, K8s Probe 설정도 8081 포트를 바라보도록 설정하는 것이 안전합니다.

## Common Pitfalls

* **`latest` 태그 사용으로 인한 롤백 불가 (Critical):** `image: ...:latest`를 사용하면 K8s는 Deployment의 변경 사항(History)을 버전별로 추적하지 못합니다. 치명적인 버그가 배포되었을 때 `kubectl rollout undo` 명령어로 이전 버전을 복구하려면 반드시 고유한 태그(예: 빌드 번호, Commit Hash)를 명시해야 합니다.
* **자원(Limits) 제한 없는 파드 배포 (Critical):** `resources.limits`를 설정하지 않으면 파드 하나가 워커 노드(EC2) 전체의 메모리를 다 써버려서 노드 자체가 다운될 수 있습니다.
* **OOMKilled와 Java 메모리 미스매치 (Critical):** K8s YAML에서 메모리 Limits를 1024Mi로 주었는데, `spring-boot-docker-build` 스킬에서 JVM 힙 메모리 최적화(`-XX:MaxRAMPercentage`) 플래그를 누락했다면, Java는 K8s의 Limit을 무시하고 메모리를 계속 할당하다가 K8s 커널에 의해 강제로 죽임(OOMKilled)을 당합니다. Dockerfile의 자원 설정과 K8s의 자원 설정은 항상 맞물려 돌아가야 합니다.
* **Readiness Probe 누락:** `readinessProbe`를 빼먹으면 K8s는 컨테이너가 뜨자마자(Spring Boot가 아직 톰캣을 띄우지도 않았는데) 트래픽을 보내버립니다. 이 경우 배포할 때마다 사용자들은 502 Bad Gateway 에러를 보게 됩니다.

## Related Skills

* `spring-boot-docker-build`: 이 K8s 매니페스트에 띄울 이미지를 생성할 때, JVM 메모리 최적화 플래그(`MaxRAMPercentage`)가 잘 설정되었는지 확인합니다.
* `spring-boot-jenkins`: 이 YAML 파일을 K8s 클러스터에 배포(Apply)하는 CD 파이프라인 연동 시 활용합니다.
