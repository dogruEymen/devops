properties([
    parameters([
        string(name: 'CODE_URL', defaultValue: ''),
        string(name: 'CREDENTIALS_ID', defaultValue: '')
    ])
])

    String codeRepoUrl = params.CODE_URL
    String credentialsId = params.CREDENTIALS_ID

    String CURRENT_VER_STAGE = 'Obtaining Current Version from pom.xml file'
node {

    
    stage('Checkout') {

        checkout(scm: [
            $class: 'GitSCM',
            branches: [[name: '*/main']],
            userRemoteConfigs: [[ url: codeRepoUrl, credentialsId: credentialsId]]
        ])

    }

    stage("Debug Workspace") {
    sh """
        pwd
        ls -la
        find . -maxdepth 3 -name pom.xml -print
    """
    }

    stage(CURRENT_VER_STAGE) {

        sh '''
            docker run --rm \
            -v "$PWD":/workspace \
            -v maven_repo:/root/.m2 \
            -w /workspace \
            maven:3.9.9-eclipse-temurin-17 \
            mvn clean package
        '''

        //echo "Current Version: ${currentVer}"

        //env.CURRENT_VER = currentVer
    }



}