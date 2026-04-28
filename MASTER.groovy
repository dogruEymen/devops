properties([
    parameters([
        string(name: 'PROJECT_ID', defaultValue: '', description: 'Lütfen projeyi seçiniz (YML dosyasındaki anahtar)')
    ])
])

node {

    currentBuild.getChangeSets().clear()
    
    checkout scm

    String BUILD_TEST_STAGE = "Build & Test"
    String SONARQUBE_STAGE = "SonarQube Check Stage"
    String VERSION_STAGE = "Version Bump"
    String DOCKER_STAGE = "Docker Build & Tag"
    String GHCR_PUSH_STAGE = "GHCR Push"

    // Jenkins Parameters
    String PROJECT_ID = params.PROJECT_ID
    println("PROJECT_ID: $PROJECT_ID")

    // Paths
    String projectsFilePath = "./projects.yml"
    
    def projects = readYaml(file: projectsFilePath)['projects']
    def project = projects[PROJECT_ID]
    String projectName = project['name']
    String projectCodeRepoUrl = project['codeRepo']['url']
    String projectCredentialsId = project['codeRepo']['credentialsId']


    deleteDir()

    try {

        stage(BUILD_TEST_STAGE) {

            build job: 'maven-build-job', wait: true, propagate: true,
            parameters: [
                string(name: 'CODE_URL', value: projectCodeRepoUrl),
                string(name: 'CREDENTIALS_ID', value: projectCredentialsId)
            ]

        }


    } catch(Exception e) {

        echo "Error occurred: ${e.message}"

    }

}