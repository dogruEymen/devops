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


    stage(CURRENT_VER_STAGE) {

        String currentVer = sh(
            script: 
            '''docker run --rm \
            -v jenkins_home:/var/jenkins_home \
            -v maven_repo:/root/.m2 \
            -w "\$WORKSPACE" \
            "\$builderImage \
            mvn help:evaluate -Dexpression=project.version -q -DforceStdout
        ''', returnStdout: true).trim()

        echo "Current Version: ${currentVer}"

        env.CURRENT_VER = currentVer
    }



}