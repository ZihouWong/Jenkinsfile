properties([
    parameters([
    // 平台选择参数选项 PublishPlatform
    choice(choices: ['Android', 'IOS', 'MAC', 'OpenHarmony'], description: '选择打包平台', name: 'PublishPlatform'),
    [
        $class: 'CascadeChoiceParameter',
        choiceType: 'PT_CHECKBOX',
        description: '''可选择单个或者多个渠道一起打包(海外渠道只支持单个打包,请勿多选)<br>PS:先锋服包必须勾选外网测试''',
        filterLength: 1,
        filterable: false,
        name: 'node_name',
        randomName: 'choice-parameter-14274211925563964',
        referencedParameters: 'PublishPlatform',
        script: [
            $class: 'GroovyScript',
            fallbackScript: [
                classpath: [],
                oldScript: '',
                sandbox: true,
                script: 'return["ERROR"]'
            ],
            script: [
                classpath: [],
                oldScript: '',
                sandbox: true,
                script:
                '''
                switch(PublishPlatform) {
                    case "Android":
                        return ["WIN1", "WIN2"]
                    case "IOS":
                        return ["MAC1", "MAC2"]
                    case "MAC":
                        return ["MAC3", "MAC4"] // 添加 MAC 选项
                    case "OpenHarmony":
                        return ["OPEN1", "OPEN2"] // 添加 OpenHarmony 选项
                    default:
                        return [] // 默认返回空数组
                }
                '''
            ]
        ]
    ],
    ]),
])

pipeline {
    agent any

    stages {
        stage('Prepare') {
            steps {
                script {
                   def utils = load 'utils.groovy'
                   utils.sayHello('world')
                }
            }
        }
    }
}
