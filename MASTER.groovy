properties([
    parameters([
        string(name: 'PROJECT_ID', defaultValue: '', description: 'Lütfen projeyi seçiniz (YML dosyasındaki anahtar)')
    ])
])

import groovy.json.JsonSlurperClassic

node {

    currentBuild.getChangeSets().clear()
    
    checkout scm
    

    // Stages
    String BUILD_TEST_STAGE = "Build & Test"
    String SONARQUBE_STAGE = "SonarQube Check Stage"
    String VERSION_STAGE = "Version Bump"
    String DOCKER_STAGE = "Docker Build & Tag"
    String GHCR_PUSH_STAGE = "GHCR Push"

    // Jobs
    String BUILD_TEST_JOB = "maven-build-job"
    String SONARQUBE_JOB = "sonarqube-check-job"
    String VERSION_JOB = "version-bump-job"
    String DOCKER_JOB = "docker-build-tag-job"
    String GHCR_PUSH_JOB = "ghcr-push-job"

    stage("Resolve Github Event") {
        if(!params.GITHUB_PAYLOAD?.trim()) {
            error "GITHUB_PAYLOAD is empty. Github webhook payload couldn't get!"
        }

        def payload = new JsonSlurperClassic().parseText(params.GITHUB_PAYLOAD)

        env.APP_NAME = payload.repository.name
        println("${env.APP_NAME}")
        println(payload)
    
    }
    // Jenkins Parameters
    String PROJECT_ID = params.PROJECT_ID
    println("PROJECT_ID: $PROJECT_ID")

    // Paths
    String projectsFilePath = "./projects.yml"
    
    def projects = readYaml(file: projectsFilePath)['projects']
    //if(!projects.projects.containKey(PROJECT_ID)){
    //    error "HATA: ${PROJECT_ID} couldn't found in YAML file!"
    //}

    def project = projects[PROJECT_ID]
    String projectName = project['name']
    String projectCodeRepoUrl = project['codeRepo']['url']
    String projectCredentialsId = project['codeRepo']['credentialsId']
    String projectBuildImage = project['images']['builderImage']
    String projectRunnerImage = project['images']['runnerImage']

    try {

        stage(BUILD_TEST_STAGE) {

            def parameters = [
                string(name: 'CODE_URL', value: projectCodeRepoUrl),
                string(name: 'CREDENTIALS_ID', value: projectCredentialsId),
                string(name: 'PROJECT_NAME', value: projectName),
                string(name: 'BUILDER_IMAGE', value: projectBuildImage)
            ]

            runDownstreamJob(BUILD_TEST_JOB, parameters)
        }

        stage(SONARQUBE_STAGE) {

            def parameters = [
                string(name: 'CODE_URL', value: projectCodeRepoUrl),
                string(name: 'CREDENTIALS_ID', value: projectCredentialsId),
                string(name: 'PROJECT_NAME', value: projectName)
            ]

            runDownstreamJob(SONARQUBE_JOB, parameters)
        }
    
        stage(VERSION_STAGE) {
            
            def parameters = [
                string(name: 'CODE_URL', value: projectCodeRepoUrl),
                string(name: 'CREDENTIALS_ID', value: projectCredentialsId),
                string(name: 'BUILDER_IMAGE', value: projectBuildImage)
            ]

            runDownstreamJob(VERSION_JOB, parameters)
        }

    } catch(Exception e) {

        echo "Error occurred: ${e.message}"

    }

}

void runDownstreamJob(String jobName, List parameters) {
    
    echo "Downstream job running: ${jobName}"

    def jobBuildData = build(
        job: jobName,
        parameters: parameters,
        wait: true,
        propagate: true
    )

    echo "Job is completed: ${jobName}"
    echo "Build number: ${jobBuildData.number}"
    echo "Build result: ${jobBuildData.result}"

}