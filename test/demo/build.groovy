


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
                    // 加载 Utils.groovy 文件并获取实例
                    env.utils = load 'Utils/utils.groovy'

                    // 检查 env.utils 是否正确
                    echo "Utils instance loaded: ${env.utils}"

                    // 获取选择的平台
                    def selectedPlatform = params.PublishPlatform
                    echo "Selected platform: ${selectedPlatform}"

                    // 调用 getHomePath 方法
                    env.HOME = env.utils.getHomePath(selectedPlatform)
                    echo "Home path: ${env.HOME}"

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