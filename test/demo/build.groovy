



def getUtils() {
    if (env.isUtilsLoad) {
        env.utils = load 'Utils/utils.groovy'
        return "true"
    } else {
        return "false"
    }

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
        utilsObject = getUtils()

        HOME = env.utils.getHomePath("${params.PublishPlatform}")
    }

    agent any

    stages {
        stage('Prepare') {
            steps {
                script {
                    echo "HOME: ${env.HOME}"
                    def demoConfig = load 'test/demo/demoConfig.groovy'
                    echo "demoConfig.name: ${demoConfig.name}"
                    echo "demoConfig.project.name: ${demoConfig.project.name}"
                    echo "demoConfig.project.version: ${demoConfig.project.version}"
                    echo "demoConfig.build.tool: ${demoConfig.build.tool}"
//                    def utils = load 'Utils/utils.groovy'
//                    echo "The name of the utility is: ${utils.name}"
////
////
                    echo "${env.utils.sayHello("Wong")}"
                    echo "${utils.sayHello("Wong")}"


                }
            }
        }
    }
}