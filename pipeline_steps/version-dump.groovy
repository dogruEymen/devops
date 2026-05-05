properties([
    parameters([
        string(name: 'CODE_URL', defaultValue: ''),
        string(name: 'CREDENTIALS_ID', defaultValue: ''),
        string(name: 'BUILDER_IMAGE', defaultValue: '')
    ])
])

    // Parameters get from upstream job
    String codeRepoUrl = params.CODE_URL
    String credentialsId = params.CREDENTIALS_ID
    String builderImage = params.BUILDER_IMAGE

    String CURRENT_VER_STAGE = 'Read Current Version from pom.xml file'
    String NEW_VERSION = 'Calculate New Version'
    String COMMIT_VERSION = 'Commit Version Change'
    String UPDATE_VERSION = 'Update Version and write on pom.xml file'
node {

    
    stage('Checkout') {
        
        cleanWs()

        checkout(scm: [
            $class: 'GitSCM',
            branches: [[name: '*/main']],
            userRemoteConfigs: [[ url: codeRepoUrl, credentialsId: credentialsId]]
        ])

    }


    stage(CURRENT_VER_STAGE) {

        String currentVer = sh(
            script: 
            """docker run --rm \\
            -v jenkins_home:/var/jenkins_home \\
            -v maven_repo:/root/.m2 \\
            -w "\${WORKSPACE}" \\
            "${builderImage}" \\
            mvn help:evaluate -Dexpression=project.version -q -DforceStdout
        """, returnStdout: true).trim()

        echo "Current Version: ${currentVer}"

        env.CURRENT_VER = currentVer

    }

    stage(NEW_VERSION) {

        String currentVer = env.CURRENT_VER

        env.NEW_VERSION = versionDumpPatch(currentVer)

        echo "New Version: ${env.NEW_VERSION}"
    
    }

    stage(UPDATE_VERSION) {

        sh(
            script: """
                docker run --rm \\
                -v jenkins_home:/var/jenkins_home \\
                -v maven_repo:/root/.m2 \\
                -w \${WORKSPACE} \\
                ${builderImage} \\
                mvn versions:set -DnewVersion=${env.NEW_VERSION} \\
                mvn versions:commit"""
        )

    }

    stage(COMMIT_VERSION) {
        
        withCredentials([
            usernamePassword(
                credentialsId: "$CREDENTIALS_ID",
                usernameVariable: "GIT_USERNAME",
                passwordVariable: "GIT_PASSWORD",
            )
        ]) {
            sh """
                git config user.name "jenkins"
                git config user.email "jenkins@local"
                
                git add pom.xml
                git commit -m "bump version to ${env.NEW_VERSION}" || echo "No version changes to commit"

                git remote set-url origin https://${GIT_USERNAME}:${GIT_PASSWORD}@${codeRepoUrl.replace("https://", "")}
                git push origin HEAD:main
                """
        }
    }



}

String versionDumpPatch(String currentVersion) {
    
    String cleanedVersion = currentVersion.replace("-SNAPSHOT", "")
    def tokenizedVer = cleanedVersion.tokenize(".")
    String updatedVer = tokenizedVer[0] + "." + tokenizedVer[1] + "."
    updatedVer = updatedVer + (tokenizedVer[2].toInteger() + 1).toString()

    if(tokenizedVer.size() != 3) {
        error "Invalid version format: ${currentVersion} Expected: major.minor.patch" 
    }

    return updatedVer

}