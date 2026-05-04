properties([
    parameters([
        string(name: 'CODE_URL', defaultValue: '', description: 'Repository URL'),
        string(name: 'CREDENTIALS_ID', defaultValue: '', description: 'Git Credentials'),
        string(name: 'PROJECT_NAME', defaultValue: '', description: 'Name of project')
    ])
])

node {  

    // Build edilecek dosyalara ihtiyacım var
    // Build edilecek ortama ve tool'lara ihtiyacım var
    // Test için gerekli tool'lara ihtiyacım var
    String projectCodeRepoUrl = params.CODE_URL
    String codeRepoCredentialsId = params.CREDENTIALS_ID
    String buildDir = "build"
    String imageName = "$PROJECT_NAME:${env.BUILD_NUMBER}"
    
    String BUILD_STAGE = "Build & Test Inside Docker"
    String TEST_STAGE = "Test"
    String SONAR_CHECK = "Code Quality Check with SonarQube"
    String QUALITY_GATE_CHECK_STAGE = "Quality Gate Check"


    stage('Checkout') {
        
        deleteDir()

        dir(buildDir) {
            checkout(scm: [
                $class: 'GitSCM', 
                branches: [[name: '*/main']],
                userRemoteConfigs: [[ url: projectCodeRepoUrl, credentialsId: codeRepoCredentialsId]]
            ])
        }

    }

    stage(BUILD_STAGE) {
        
        dir(buildDir){

            echo 'Building project...'
            // Yalnızca build sonucu oluşan dosyaları alıyoruz
            sh 'docker build --target artifacts --output type=local,dest=./out .'
            // Oluşan image dosyasını alıyoruz
            sh 'docker build --target runner -t ${PROJECT_ID}:${env.BUILD_NUMBER} .'

        }
        
    }


    stage(SONAR_CHECK) {

        dir(buildDir){

            withSonarQubeEnv('sonarQube') {

                sh 'mvn sonar:sonar'

            }
        }

    }

    stage(QUALITY_GATE_CHECK_STAGE) {
            
        timeout(time: 1, unit: 'HOURS') {

            waitForQualityGate abortPipeline: true  

        }

    }



}
