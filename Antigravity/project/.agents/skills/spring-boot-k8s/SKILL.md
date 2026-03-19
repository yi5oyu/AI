---
name: spring-boot-k8s
description: Write Kubernetes Deployment and Service YAML manifests optimized for Spring Boot applications to configure zero-downtime deployments and resource-optimized environments.
argument-hint: "[Application name, Docker image path, port number, Spring Profile (e.g., Write Deployment and Service YAMLs to deploy the v1.0.0 image of my-app on port 8080)]"
source: "custom"
tags: ["kubernetes", "k8s", "spring-boot", "devops", "deployment", "msa", "infrastructure"]
triggers:
  - "쿠버네티스 배포"
  - "K8s YAML 작성"
---

# Spring Boot Kubernetes Manifests

Declarative YAML authoring guidelines for reliably deploying and serving Docker-containerized Spring Boot applications on a Kubernetes cluster.

## Overview

- **Deployment (Rollout & Management):** Maintains the desired number of Pod replicas and handles Rolling Updates. You MUST configure `Liveness Probe` and `Readiness Probe` so K8s can accurately perceive the state of the Spring Boot application.
- **Service (Network Exposure):** Groups the dynamic IPs of created/deleted Pods into a single endpoint to route internal or external traffic.
- **Resource Isolation (Resource Quota):** Strictly configures `requests` and `limits` to prevent the application from consuming the entire cluster's memory.

## When to Use This Skill

- When you need to deploy Docker images pushed to the registry (e.g., via the `spring-boot-jenkins` pipeline) onto a production/development K8s cluster.
- When you need to scale out multiple containers (Pods) to handle traffic loads and apply load balancing.
- When configuring a safe rolling update architecture to eliminate downtime during deployments.

## How It Works

### Step 1: Add Spring Boot Actuator

For Kubernetes to accurately monitor the application's health, the `spring-boot-starter-actuator` dependency must be added to the Spring Boot project (`build.gradle`).
- You need to expose the K8s-specific health check group in `application.yml` (`management.endpoint.health.probes.enabled=true`).

### Step 2: Define the `Service`

Write network policies for communicating with Pods inside the cluster.
- Map the labels of the Pods to receive traffic (`app: my-spring-app`) using the `selector`.
- By default, use `type: ClusterIP` to construct an internal cluster network. (Use Ingress or LoadBalancer separately for external exposure).

### Step 3: Define the `Deployment`

Write the application execution specifications.
- **Resources:** Set the minimum guaranteed amount (`requests`) and the maximum limit (`limits`) of CPU and Memory the container can use.
- **Probes:** - `readinessProbe`: Calls `/actuator/health/readiness`. K8s only routes traffic to the Pod if it receives a 200 OK. (Crucial for zero-downtime deployments).
  - `livenessProbe`: Calls `/actuator/health/liveness`. If there's no response, K8s kills the Pod and restarts it. (Auto-recovery).
- **Lifecycle:** Configure a `preStop` hook to ensure ongoing requests are not abruptly cut off when the application terminates.

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
  type: ClusterIP # For internal cluster communication (use LoadBalancer if external exposure is needed)
  selector:
    app: my-spring-app
  ports:
    - name: http
      protocol: TCP
      port: 8080        # Port exposed by the service
      targetPort: 8080  # Port the container listens on

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-spring-app-deployment
  namespace: default
  labels:
    app: my-spring-app
spec:
  replicas: 2 # Launch 2 default pods (Ensure high availability)
  selector:
    matchLabels:
      app: my-spring-app
  strategy:
    type: RollingUpdate # Zero-downtime deployment strategy
    rollingUpdate:
      maxSurge: 1       # Max number of extra pods during update
      maxUnavailable: 0 # Maintain at least 100% capacity during update
  template:
    metadata:
      labels:
        app: my-spring-app
    spec:
      containers:
        - name: my-spring-app-container
          # Caution: Avoid using 'latest' in production. Use explicit versions.
          image: my-docker-repo/spring-boot-msa-app:v1.0.0
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          
          # Inject Environment Variables (e.g., Spring Profiles)
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: TZ
              value: "Asia/Seoul"
              
          # Container Resource Allocation (Prevent OOM)
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "500m"
              memory: "1024Mi"
              
          # Readiness Probe: Is it ready to receive traffic? (Core to zero-downtime)
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 15 # Wait time for app startup
            periodSeconds: 10
            failureThreshold: 3
            
          # Liveness Probe: Is the app alive? (Restarts pod if no response)
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 20
            failureThreshold: 3
            
          # Lifecycle: Provide a brief wait time before SIGTERM for Graceful Shutdown
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 10"]

```

## Best Practices

* **Spring Boot Graceful Shutdown Integration:** For flawless zero-downtime deployments, combine Kubernetes' `preStop` wait time with the `server.shutdown=graceful` setting in Spring Boot's `application.yml`. This forces the application to wait until ongoing HTTP requests are completed before shutting down.
* **Actuator Port Separation (Enhanced Security):** Exposing the `/actuator` endpoint on port 8080, which receives public traffic, is a security risk. It is safer to separate it (e.g., `management.server.port=8081`) and configure the K8s Probes to target port 8081, restricting access to the internal network.

## Common Pitfalls

* **Inability to Rollback due to `latest` Tag (Critical):** If you use `image: ...:latest`, K8s cannot track the version history of the Deployment. To recover a previous version using `kubectl rollout undo` when a fatal bug is deployed, you MUST specify a unique tag (e.g., Build Number, Commit Hash).
* **Deploying Pods without Resource Limits (Critical):** If `resources.limits` is not set, a single Pod can consume all the memory of an entire worker node (EC2), potentially crashing the node itself.
* **OOMKilled & Java Memory Mismatch (Critical):** If you allocate a 1024Mi memory Limit in the K8s YAML but fail to set the JVM heap optimization flag (`-XX:MaxRAMPercentage`) in the `spring-boot-docker-build` skill, Java will ignore the K8s limit and keep allocating memory until the K8s kernel forcibly kills it (OOMKilled). Dockerfile resource settings and K8s resource settings must always align.
* **Missing Readiness Probe:** If you omit `readinessProbe`, K8s will send traffic to the container the moment it starts (even before Spring Boot spins up Tomcat). In this scenario, users will face 502 Bad Gateway errors every time you deploy.

## Related Skills

* `spring-boot-docker-build`: Check if the JVM memory optimization flag (`MaxRAMPercentage`) is properly configured when creating the image to be launched in this K8s manifest.
* `spring-boot-jenkins`: Utilize this YAML file when integrating the CD pipeline to apply the manifest to the K8s cluster.
