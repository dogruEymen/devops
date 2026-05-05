properties([
    parameters([
        string(name: 'REPO_URL', defaultValue: ''),
        string(name: 'CREDENTIALS_ID', defaultValue: ''),
        string(name: 'PROJECT_NAME', defaultValue: '')
    ])
])

node {

    // Stages
    String SONAR_STAGE = "SonarQube Analysis Stage"
    String SONAR_CHECK = "Code Quality Check with SonarQube"

    // Naming
    String SONARQUBE_SERVER_NAME = "sonarQube"
    String SONAR_SCANNER_TOOL = "SonarScanner"

    // parameters
    String projectName = params.PROJECT_NAME
    String projectCodeRepoUrl = params.REPO_URL
    String projectCredentialsId = params.CREDENTIALS_ID

    
    stage("Checkout") {
        // önceki buildten kalan dosyaları temizler
        cleanWs() //clean workspace

        checkout(scm: [
            $class: 'GitSCM',
            branches: [name: '*/main'],
            userRemoteConfigs: [[ url: projectCodeRepoUrl, credentialsId: projectCredentialsId]]
        ])

    }

    stage(SONAR_STAGE) {

        echo "Running SonarQube analysis for ${projectName}"

        withSonarQubeEnv(SONARQUBE_SERVER_NAME) {
            
            // Jenkins’te tanımlı SonarScanner tool’unun kurulu olduğu path’i verir.
            String scannerHome = tool SONAR_SCANNER_TOOL
            sh """
                ${scannerHome}/bin/sonar-scanner \
                -Dsonar.projectKey=${projectName} \
                -Dsonar.projectName=${projectName} \
                -Dsonar.sources=. \
                -Dsonar.java.binaries=. \
            """ 
        }
    }

    stage(SONAR_CHECK) {

        timeout(time: 5, unit: "MINUTES") {
            
            def qualityGate = waitForQualityGate()
            if(qualityGate.status != "OK") {
                error "SonarQube Quality Gate failed. Status : ${qualityGate.status}"
            }

            echo "SonarQube Quality Gate passed."
        }
    }
}