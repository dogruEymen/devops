properties([
    parameters([
        string(name: 'CODE_URL', defaultValue: '', description: 'Repository URL'),
        string(name: 'CREDENTIALS_ID', defaultValue: '', description: 'Git Credentials')
    ])
])

node {

    String mavenHome = tool 'maven_3.9'

    withEnv(["PATH+MAVEN=${mavenHome}/bin"]){
        
        deleteDir()

        // Build edilecek dosyalara ihtiyacım var
        // Build edilecek ortama ve tool'lara ihtiyacım var
        // Test için gerekli tool'lara ihtiyacım var
        String projectCodeRepoUrl = params.CODE_URL
        String codeRepoCredentialsId = params.CREDENTIALS_ID
        String buildDir = "build"

        String BUILD_STAGE = 'Build'
        String TEST_STAGE = 'Test'
        String SONAR_CHECK = "Code Quality Check with SonarQube"
        String QUALITY_GATE_CHECK_STAGE = "Quality Gate Check"


        stage('Checkout') {

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
                sh 'mvn clean package -DskipTests'

            }
            
        }

        stage(TEST_STAGE) {
            
            dir(buildDir){
    
                echo 'Testing project...'
                sh 'mvn test'

            }
            
        }

        stage(SONAR_CHECK) {

            withSonarQubeEnv('sonarQube') {

                sh 'mvn sonar:sonar'

            }

        }

        stage(QUALITY_GATE_CHECK_STAGE) {
                
            timeout(time: 1, unit: 'HOURS') {

                waitForQualityGate abortPipeline: true  

            }

        }

    }

}
