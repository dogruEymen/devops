# DevOps CI/CD Pipeline Orchestrator

A Jenkins-based DevOps automation project for building, testing, versioning, analyzing, and publishing Java applications through reusable pipeline jobs.

This repository acts as a central CI/CD controller. It receives GitHub webhook events, resolves the target application from `projects.yml`, and delegates work to downstream Jenkins jobs for build/test, SonarQube analysis, semantic patch versioning, and Docker image publication to GitHub Container Registry.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Repository Structure](#repository-structure)
- [Pipeline Flow](#pipeline-flow)
- [Prerequisites](#prerequisites)
- [Jenkins Jobs](#jenkins-jobs)
- [Configuration](#configuration)
- [SonarQube](#sonarqube)
- [Docker Image Publishing](#docker-image-publishing)
- [Versioning](#versioning)
- [Webhook Behavior](#webhook-behavior)
- [Getting Started](#getting-started)
- [Troubleshooting](#troubleshooting)

## Overview

The project is designed for teams that manage multiple Java services and want a shared CI/CD pipeline model. Application-specific details are stored in `projects.yml`, while pipeline behavior is split into reusable Groovy pipeline scripts.

The master pipeline is responsible for:

- Receiving GitHub webhook events.
- Detecting whether the event is a pull request, merge, or push.
- Skipping builds triggered by Jenkins-generated version commits.
- Loading project metadata from `projects.yml`.
- Running downstream Jenkins jobs with the correct repository, credentials, and Docker image parameters.

## Architecture

```text
GitHub Webhook
      |
      v
MASTER.groovy
      |
      +--> maven-build-job
      |        `-- pipeline_steps/buildAndTest.groovy
      |
      +--> sonarqube-check-job
      |        `-- pipeline_steps/sonar-check.groovy
      |
      +--> version-bump-job
      |        `-- pipeline_steps/version-dump.groovy
      |
      `--> docker-build-push-job
               `-- pipeline_steps/docker-build-tag.groovy
```

## Repository Structure

```text
.
+-- MASTER.groovy
+-- README.md
+-- docker-compose.yml
+-- projects.yml
`-- pipeline_steps
    +-- buildAndTest.groovy
    +-- docker-build-tag.groovy
    +-- sonar-check.groovy
    `-- version-dump.groovy
```

| Path | Purpose |
| --- | --- |
| `MASTER.groovy` | Main Jenkins pipeline that handles webhook events and orchestrates downstream jobs. |
| `projects.yml` | Project registry containing repository URLs, Jenkins credentials IDs, repository slugs, and Docker image settings. |
| `docker-compose.yml` | Local SonarQube and PostgreSQL stack for code quality analysis. |
| `pipeline_steps/buildAndTest.groovy` | Checks out an application repository and builds/tests it using Docker. |
| `pipeline_steps/sonar-check.groovy` | Runs SonarQube analysis and enforces the Quality Gate. |
| `pipeline_steps/version-dump.groovy` | Reads the Maven project version, increments the patch version, commits it, and pushes back to `main`. |
| `pipeline_steps/docker-build-tag.groovy` | Builds a Docker image, tags it with Maven version and commit SHA, test-runs it, and pushes it to GHCR. |

## Pipeline Flow

1. GitHub sends a webhook event to Jenkins.
2. `MASTER.groovy` extracts event metadata such as repository name, pull request state, commit SHA, branch reference, and sender information.
3. The pipeline skips Jenkins-generated commits or commits containing `[jenkins skip]` or `[skip ci]`.
4. The repository name is matched against `projects.yml`.
5. The build/test job runs for the target application.
6. SonarQube analysis can be enabled as a quality gate stage.
7. If the event represents a merge or push, the pipeline bumps the Maven patch version.
8. The Docker build job creates and pushes a GHCR image tagged as:

```text
ghcr.io/<owner>/<repository>:<project.version>-<short-commit-sha>
```

## Prerequisites

Install and configure the following before running the pipelines:

- Jenkins with Pipeline support.
- Jenkins agents with Docker access.
- GitHub webhook configured for the master Jenkins job.
- Git credentials configured in Jenkins for each application repository.
- GitHub Container Registry credentials configured in Jenkins.
- Maven-based Java applications with a valid `pom.xml`.
- Application Dockerfiles that support the build arguments used by the pipeline.
- Docker Compose for running the local SonarQube stack.

Recommended Jenkins plugins:

- Generic Webhook Trigger
- Pipeline Utility Steps
- Git
- Credentials Binding
- Workspace Cleanup
- SonarQube Scanner for Jenkins

## Jenkins Jobs

Create the following Jenkins Pipeline jobs and point each job to the matching Groovy script:

| Jenkins Job | Pipeline Script |
| --- | --- |
| `master` or your chosen orchestrator job | `MASTER.groovy` |
| `maven-build-job` | `pipeline_steps/buildAndTest.groovy` |
| `sonarqube-check-job` | `pipeline_steps/sonar-check.groovy` |
| `version-bump-job` | `pipeline_steps/version-dump.groovy` |
| `docker-build-push-job` | `pipeline_steps/docker-build-tag.groovy` |

The master pipeline expects the downstream job names above. If you use different Jenkins job names, update these constants in `MASTER.groovy`:

```groovy
String BUILD_TEST_JOB = "maven-build-job"
String SONARQUBE_JOB = "sonarqube-check-job"
String VERSION_JOB = "version-bump-job"
String DOCKER_BUILD_PUSH_JOB = "docker-build-push-job"
```

## Configuration

Applications are registered in `projects.yml`.

```yaml
projects:
  java-project-1:
    codeRepo:
      url: "https://github.com/dogruEymen/java-project-1.git"
      credentialsId: "java-project-1-credentials"
      repoSlug: "dogruEymen/java-project-1"
    images:
      builderImage: "maven:3.9-eclipse-temurin-17"
      runnerImage: "eclipse-temurin:17-jre-jammy"
```

Each project entry should include:

| Field | Description |
| --- | --- |
| `codeRepo.url` | Git URL of the application repository. |
| `codeRepo.credentialsId` | Jenkins credentials ID used to clone and push to the repository. |
| `codeRepo.repoSlug` | GitHub repository slug used when publishing to GHCR. |
| `images.builderImage` | Docker image used for Maven build and version extraction. |
| `images.runnerImage` | Runtime base image passed to the application Docker build. |

The GitHub repository name must match a key under `projects`, because `MASTER.groovy` resolves the project using `env.repo_name`.

## SonarQube

The project includes a Docker Compose stack for SonarQube Community Edition with PostgreSQL.

Start SonarQube:

```bash
docker compose up -d
```

SonarQube will be available at:

```text
http://localhost:9000
```

The SonarQube pipeline expects:

- A Jenkins SonarQube server named `sonarQube`.
- A Jenkins scanner tool named `SonarScanner`.
- A quality gate configured in SonarQube.

The SonarQube stage is currently present as a reusable downstream job. In `MASTER.groovy`, the SonarQube stage is commented out and can be re-enabled when the SonarQube server and Jenkins tool configuration are ready.

## Docker Image Publishing

`pipeline_steps/docker-build-tag.groovy` builds and publishes images to GitHub Container Registry.

The job:

- Checks out the configured application repository.
- Reads the Maven project version from `pom.xml`.
- Creates an image tag using the Maven version and short commit SHA.
- Builds the Docker image with `BASE_IMAGE` and `RUNNER_IMAGE` build arguments.
- Starts the image once for a basic runtime check.
- Logs in to `ghcr.io`.
- Pushes the image to GHCR.

Required Jenkins credential:

| Credentials ID | Purpose |
| --- | --- |
| `github-access-token` | Username/password credential used for `docker login ghcr.io`. |

## Versioning

`pipeline_steps/version-dump.groovy` increments the Maven patch version.

Example:

```text
1.2.3 -> 1.2.4
1.2.3-SNAPSHOT -> 1.2.4
```

After updating `pom.xml`, the job commits the change with:

```text
bump version to <new-version> [jenkins skip]
```

The `[jenkins skip]` marker prevents the version bump commit from triggering another pipeline run.

## Webhook Behavior

The master job uses the Generic Webhook Trigger plugin and listens with this token:

```text
master-generic-webhook-token
```

Expected GitHub events:

- Pull request opened: runs build/test.
- Pull request closed without merge: skips the pipeline.
- Pull request merged: runs merge flow, including version bump and Docker publishing.
- Push event: treated as a merge-style event and can run version bump and Docker publishing.

The pipeline also skips builds when:

- The commit message contains `[jenkins skip]`.
- The commit message contains `[skip ci]`.
- The pusher is `jenkins`.

## Getting Started

1. Clone this repository onto your Jenkins controller or configure each Jenkins Pipeline job to load its Groovy file from SCM.
2. Start SonarQube if quality analysis is required:

```bash
docker compose up -d
```

3. Create Jenkins credentials for each application repository.
4. Create the `github-access-token` Jenkins credential for GHCR publishing.
5. Configure the downstream Jenkins jobs listed in [Jenkins Jobs](#jenkins-jobs).
6. Add each application to `projects.yml`.
7. Configure a GitHub webhook that targets the master Jenkins job and uses the token `master-generic-webhook-token`.
8. Open or merge a pull request in a registered application repository to trigger the pipeline.

## Troubleshooting

### Project is not found

Make sure the GitHub repository name exactly matches a key under `projects` in `projects.yml`.

### Docker push fails

Verify that:

- The `github-access-token` credential exists in Jenkins.
- The token has permission to publish packages.
- The `repoSlug` field is set to `<owner>/<repository>`.

### Version bump does not push

Verify that the application repository credential has write access and that branch protection rules allow Jenkins to push to `main`.

### SonarQube quality gate fails

Open the SonarQube project dashboard, review the failed gate conditions, and fix the reported issues before merging.

### Pipeline keeps retriggering

Confirm that Jenkins version bump commits include `[jenkins skip]` and that the webhook payload provides the expected pusher and commit message fields.

## License

No license file is currently included. Add a license before distributing or publishing this project.
