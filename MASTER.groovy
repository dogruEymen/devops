properties([
    disableConcurrentBuilds(),

    parameters([
        string(name: 'BRANCH_NAME', defaultValue: 'main')
    ]),
    pipelineTriggers([
        [$class: 'GenericTrigger',
            
            genericVariables: [
                [
                    key: 'github_action',
                    value: '$.action',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'pr_merged',
                    value: '$.pull_request.merged',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'pr_number',
                    value: '$.pull_request.number',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'commit_sha',
                    value: '$.pull_request.head.sha',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'repo_name',
                    value: '$.repository.name',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'repo_full_name',
                    value: '$.repository.full_name',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'push_ref',
                    value: '$.ref',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'push_after',
                    value: '$.after',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'webhook_commit_message',
                    value: '$.head_commit.message',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'webhook_pusher_name',
                    value: '$.pusher.name',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ],
                [
                    key: 'webhook_sender_login',
                    value: '$.sender.login',
                    expressionType: 'JSONPath',
                    defaultValue: ''
                ]
            ],
            
            genericHeaderVariables: [
                [
                    key: 'X-GitHub-Event',
                    regexpFilter: ''
                ]
            ],
            
            token: 'master-generic-webhook-token',
            
            causeString: 'Triggered by GitHub event',
            
            printContributedVariables: true,
            printPostContend: true,
            
            silentResponse: false
        ]
    ])
])

//import groovy.json.JsonSlurperClassic

boolean skipPipeline = false
boolean isAMerge = false

node {

    stage("Pre-Check for Pipeline") {
        
        String commitMessage = env.webhook_commit_message ?: ''
        String pusherName = env.webhook_pusher_name ?: ''
        String senderLogin = env.webhook_sender_login ?: ''

        echo "Commit Message: ${commitMessage}"
        echo "Pusher Name: ${pusherName}"
        echo "Sender Login: ${senderLogin}"

        // Check if commit triggered by Jenkins?
        skipPipeline = shouldSkipBuild(commitMessage, pusherName, senderLogin)

        if(env.github_action == "closed" && env.pr_merged == false){
            echo "Job is stopping because PR is closed."
            skipPipeline = true
        }
        if(skipPipeline) {
            echo "Job is stopping because it's triggered by Jenkins/version bump"
            currentBuild.result = 'NOT_BUILD'
        }

    }

    if(skipPipeline) {
        return
    }

    stage("Check for Merge") {
        
        if(env.github_action == null && env.pr_merged == null &&  env.x_github_event == "push"){
            isAMerge = true
        }
        if(env.github_action == "closed" && env.pr_merged == true){
            isAMerge = true
        }
        if(env.github_action == "opened" && env.pr_merged == false){
            isAMerge = false
        }
    }
    
    stage("Debug Webhook Variables") {
        echo "github_action = ${env.github_action}"
        echo "pr_merged = ${env.pr_merged}"
        echo "pr_number = ${env.pr_number}"
        echo "commit_sha = ${env.commit_sha}"
        echo "repo_url = ${env.repo_url}"
        echo "repo_name = ${env.repo_name}"
        echo "repo_full_name = ${env.repo_full_name}"
        echo "push_ref = ${env.push_ref}"
        echo "push_after = ${env.push_after}"
        echo "x_github_event = ${env.x_github_event}"
    }

    currentBuild.getChangeSets().clear()
    
    checkout scm
    

    // Stages
    String BUILD_TEST_STAGE = "Build & Test"
    String SONARQUBE_STAGE = "SonarQube Check Stage"
    String VERSION_STAGE = "Version Bump"
    String DOCKER_STAGE = "Docker Build, Tag & Push"
    

    // Jobs
    String BUILD_TEST_JOB = "maven-build-job"
    String SONARQUBE_JOB = "sonarqube-check-job"
    String VERSION_JOB = "version-bump-job"
    String DOCKER_BUILD_PUSH_JOB = "docker-build-push-job"

    /*stage("Resolve Github Event") {
        if(!params.GITHUB_PAYLOAD?.trim()) {
            error "GITHUB_PAYLOAD is empty. Github webhook payload couldn't get!"
        }

        def payload = new JsonSlurperClassic().parseText(params.GITHUB_PAYLOAD)

        env.APP_NAME = payload.repository.name
        println("${env.APP_NAME}")
        println(payload)
    
    }
    */
    // Jenkins Parameters
    String PROJECT_ID = env.repo_name
    println("PROJECT_ID: $PROJECT_ID")
    String BRANCH_NAME = params.BRANCH_NAME
    println("BRANCH_NAME: $BRANCH_NAME")

    // Paths
    String projectsFilePath = "./projects.yml"
    
    def projects = readYaml(file: projectsFilePath)['projects']
    //if(!projects.projects.containKey(PROJECT_ID)){
    //    error "HATA: ${PROJECT_ID} couldn't found in YAML file!"
    //}

    def project = projects[PROJECT_ID]
    String projectName = env.repo_name
    String projectCodeRepoUrl = project['codeRepo']['url']
    String projectCredentialsId = project['codeRepo']['credentialsId']
    String projectBuildImage = project['images']['builderImage']
    String projectRunnerImage = project['images']['runnerImage']
    String repoSlug = project['codeRepo']['repoSlug']

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

        /*stage(SONARQUBE_STAGE) {

            def parameters = [
                string(name: 'CODE_URL', value: projectCodeRepoUrl),
                string(name: 'CREDENTIALS_ID', value: projectCredentialsId),
                string(name: 'PROJECT_NAME', value: projectName)
            ]

            runDownstreamJob(SONARQUBE_JOB, parameters)
        }*/
    

        if (isAMerge) {

            stage(VERSION_STAGE) {
            
                def parameters = [
                    string(name: 'CODE_URL', value: projectCodeRepoUrl),
                    string(name: 'CREDENTIALS_ID', value: projectCredentialsId),
                    string(name: 'BUILDER_IMAGE', value: projectBuildImage)
                ]

                runDownstreamJob(VERSION_JOB, parameters)
            }

            stage(DOCKER_STAGE) {

                def parameters = [
                    string(name: 'CODE_URL', value: projectCodeRepoUrl),
                    string(name: 'BRANCH_NAME', value: BRANCH_NAME),
                    string(name: 'CREDENTIALS_ID', value: projectCredentialsId),
                    string(name: 'REPO_SLUG', value:repoSlug),
                    string(name: 'BUILD_IMAGE', value: projectBuildImage),
                    string(name: 'RUNNER_IMAGE', value: projectRunnerImage),
                    string(name: 'PROJECT_NAME', value: projectName)
                ]

                runDownstreamJob(DOCKER_BUILD_PUSH_JOB, parameters)
            }
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

boolean shouldSkipBuild(String commitMessage, String pusherName, String senderLogin) {

    String msg = commitMessage ?: ''
    String pusher = pusherName ?: ''
    String sender = senderLogin ?: ''

    if (msg.contains('[jenkins skip]') || msg.contains('[skip ci]')) {
        return true
    }
    
    if (pusher == 'jenkins') {
        return true
    }

    return false
}