

//def utilsClass = this.class.classLoader.parseClass(new File('utils.groovy'))
//def utils = utilsClass.newInstance() // 创建 utils 类的实例



properties([
        parameters([
                // 平台选择参数选项 PublishPlatform
                choice(choices: ['Android', 'IOS', 'MAC', 'OpenHarmony'], description: '选择打包平台', name: 'PublishPlatform'),

        ]),
])

pipeline {
    agent any

    stages {
        stage('Prepare') {
            steps {
                script {
                    def demoConfig
                    demoConfig = load 'test/demo/demoConfig.groovy'
                    echo "demoConfig.name: ${demoConfig.name}"
                    echo "demoConfig.project.name: ${demoConfig.project.name}"
                    echo "demoConfig.project.version: ${demoConfig.project.version}"
                    echo "demoConfig.build.tool: ${demoConfig.build.tool}"

                    def utils
                    utils = load 'Utils/utils.groovy'
                    echo "utilsName: ${utils.utilsName}"


                    utils.sayHello("huangzhihao")




                }
            }
        }
    }
}