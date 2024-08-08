

def getHomePath(String platform) {
    def utils = load 'Utils/utils.groovy'
    return utils.getHomePath(platform)
}


properties([
        parameters(
                [
                // 平台选择参数选项 PublishPlatform
                choice(choices: ['Android', 'IOS', 'MAC', 'OpenHarmony'], description: '选择打包平台', name: 'PublishPlatform'),

        ])
])

pipeline {
    environment {

        HOME = getHomePath("$params.PublishPlafrom}")
    }

    agent any

    stages {
        stage('Prepare') {
            steps {
                script {
                    echo "${env.HOME}"
                    def demoConfig = load 'test/demo/demoConfig.groovy'
                    echo "demoConfig.name: ${demoConfig.name}"
                    echo "demoConfig.project.name: ${demoConfig.project.name}"
                    echo "demoConfig.project.version: ${demoConfig.project.version}"
                    echo "demoConfig.build.tool: ${demoConfig.build.tool}"
//                    def utils = load 'Utils/utils.groovy'
//                    echo "The name of the utility is: ${utils.name}"
////
////
                    echo "${utils.sayHello("Wong")}"
                    echo "${utils.sayHello2("Wong")}"


                }
            }
        }
    }
}