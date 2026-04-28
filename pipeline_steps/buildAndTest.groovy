properties([
    parameters([
        string(name: 'CODE_URL', defaultValue: '', description: 'Repository URL'),
        string(name: 'CREDENTIALS_ID', defaultValue: '', description: 'Git Credentials')
    ])
])

node {

    deleteDir()

    // Build edilecek dosyalara ihtiyacım var
    // Build edilecek ortama ve tool'lara ihtiyacım var
    // Test için gerekli tool'lara ihtiyacım var
    String projectCodeRepoUrl = params.CODE_URL
    String codeRepoCredentialsId = params.CREDENTIALS_ID

    String BUILD_STAGE = 'Build'
    String TEST_STAGE = 'Test'
    String SONAR_CHECK = "Code Quality Check with SonarQube"
    String QUALITY_GATE_CHECK_STAGE = "Quality Gate Check"
    
    stage(BUILD_STAGE) {
        
        String buildDir = "build"
        dir(buildDir){
            checkout(
            scm: [$class: 'GitSCM', 
                  branches: [[name: '*/main']],
                  userRemoteConfigs: [[ url: projectCodeRepoUrl, credentialsId: codeRepoCredentialsId]]
                  ])

            buildMaven()
        }

        
        
    }

    stage(TEST_STAGE) {
        String testDir = "test"
        dir(testDir){
            checkout(
            scm: [$class: 'GitSCM', 
                  branches: [[name: '*/main']],
                  userRemoteConfigs: [[ url: projectCodeRepoUrl, credentialsId: codeRepoCredentialsId]]
                  ])

            testMaven()
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

def buildMaven(){

    echo 'Build maven running...'

    String mavenPath = tool 'maven_3.9'
    String mavenBuildPath = "PATH+MAVEN=$mavenPath/bin"

    withEnv([mavenBuildPath]){
        sh 'mvn clean package'
    }

}

def testMaven(){

    echo 'Test maven running...'

    String mavenPath = tool 'maven_3.9'
    String mavenBuildPath = "PATH+MAVEN=$mavenPath/bin"

    withEnv([mavenBuildPath]){
        sh 'mvn test'
    }
}