


properties([
        parameters(
                [
                // 平台选择参数选项 PublishPlatform
                choice(choices: ['Android', 'IOS', 'MAC', 'OpenHarmony'], description: '选择打包平台', name: 'PublishPlatform'),

        ])
])

pipeline {


    agent any

    stages {

        stage('Prepare') {
            steps {
                script {
                    echo "environment"
                    env.utils = load 'Utils/utils.groovy'
                    def selectedPlatform = params.PublishPlatform
                    echo "Selected platform: ${selectedPlatform}"
                    env.HOME = env.utils.getHomePath(selectedPlatform)

                }
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
//                    echo "${Utils.sayHello("Wong")}"



                }
            }
        }
    }
}