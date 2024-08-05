//node { // 加入 node 块以提供必要的 FilePath 上下文
//    def utils = load 'test/utils.groovy'
//    stage("say Hello") {
//        utils.sayHello()
//    }
//
//}
//
//def greetingOutside = utils.sayHello("World")
//println(greetingOutside)
utils = this.class.classLoader.parseClass("utils.groovy")
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
                    utils.sayHello()

//                    def greeting = utils.sayHello("World")
//                    println(greeting)


                }
            }
        }
    }
}
