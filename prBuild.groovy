node {

    deleteDir()

    String BRANCH_NAME = params.BRANCH_NAME
    println("BRANCH NAME: $BRANCH_NAME")



    // Stages
    String BUILD_STAGE = 'Build'

    String buildDir = "build"

    dir(buildDir){

        checkout(scm: scm, changelog: false)
        
    }
    

    

}