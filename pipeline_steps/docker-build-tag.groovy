properties([
    parameters([
        string(name: 'CODE_URL', defaultValue: ''),
        string(name: 'BRANCH_NAME', defaultValue: 'main'),
        string(name: 'CREDENTIALS_ID', defaultValue: ''),
        string(name: 'REPO_SLUG', defaultValue: ''),
        string(name: 'BUILD_IMAGE', defaultValue: ''),
        string(name: 'RUNNER_IMAGE', defaultValue: ''),
        string(name: 'PROJECT_NAME', defaultValue: '')
    ])
])


node {

    // Parameters
    String CODE_URL = params.CODE_URL
    String BRANCH_NAME = params.BRANCH_NAME
    String CREDENTIALS_ID = params.CREDENTIALS_ID
    String REPO_SLUG = params.REPO_SLUG

    String registry = 'ghcr.io'
    String buildImage = params.BUILD_IMAGE
    String runnerImage = params.RUNNER_IMAGE
    String projectName = params.PROJECT_NAME

    // Stages
    String BUILD_AND_PUSH_STAGE = "Build Docker Image & Push to the Repo"
    String IMAGE_TAG_STAGE = "Image Tag Stage"
    String JAR_PUBLISH_STAGE = "Build & Publish JAR to GitHub Packages"

    stage('Checkout') {

        cleanWs()

        checkout([
            $class: "GitSCM",
            branches: [[name: "*/${BRANCH_NAME}"]],
            userRemoteConfigs: [[
                url: CODE_URL,
                credentialsId: CREDENTIALS_ID
            ]]
        ])
    }
    
    stage(IMAGE_TAG_STAGE) {

        String commitSHA = sh(
            script: "git rev-parse HEAD",
            returnStdout: true
        ).trim()

        env.SHORT_COMMIT = commitSHA.take(7)
        env.VERSION_TAG = getProjectVersion(buildImage)
        env.IMAGE_NAME = "${registry}/${REPO_SLUG}:${env.VERSION_TAG}-${env.SHORT_COMMIT}".toLowerCase()

    }

    stage(JAR_PUBLISH_STAGE) {
        withCredentials([usernamePassword(
            credentialsId: CREDENTIALS_ID,
            usernameVariable: "GITHUB_USER",
            passwordVariable: "GITHUB_PASS"
        )]) {
            sh """
    mkdir -p .m2

    cat > .m2/settings.xml << EOF

    <settings>
        <servers>
            <server>
                <id>github</id>
                <username>${GITHUB_USER}</username>
                <password>${GITHUB_PASS}</password>
            </server>
        </servers>
    </settings>
    EOF
    """

        sh """
            docker run --rm \\
            -v "\${WORKSPACE}:/workspace" \\
            -v "\${WORKSPACE}/.m2/settings.xml:/root/.m2/settings.xml" \\
            -v maven_repo:/root/.m2/repository \\
            -w /workspace \\
            "${buildImage}" \\
            mvn clean deploy -DskipTests

        """
        }
    }
    stage(BUILD_AND_PUSH_STAGE) {
        sh """docker build --build-arg BASE_IMAGE=${buildImage} \
        --build-arg RUNNER_IMAGE=${runnerImage} \
        -t ${env.IMAGE_NAME} ."""

        echo "Container starting..."
        sh "docker rm -f ${projectName}-${env.VERSION_TAG} || true"
        sh "docker run -d -p 4040:4040 --name ${projectName}-${env.VERSION_TAG} ${env.IMAGE_NAME}"

        withCredentials([usernamePassword(
            credentialsId: 'github-webhook',
            usernameVariable: "GHCR_USER",
            passwordVariable: "GHCR_PASS"
        )]) {

            echo 'GHCR Logging in...'
            sh "echo ${GHCR_PASS} | docker login ${registry} -u ${GHCR_USER} --password-stdin"

            echo "Image pushing..."
            sh "docker push ${env.IMAGE_NAME}"
        }
    }

    
}


String getProjectVersion(String buildImage) {

    String currentVer = sh(
        script: 
            """docker run --rm \\
            -v jenkins_home:/var/jenkins_home \\
            -v maven_repo:/root/.m2 \\
            -w "\${WORKSPACE}" \\
            "${buildImage}" \\
            mvn help:evaluate -Dexpression=project.version -q -DforceStdout
        """, returnStdout: true).trim()

    if(!currentVer) {
        error "Project version couldn't get from pom.xml file!"
    }

    return currentVer
}
