// 1. 脚本方法

// 1). 项目私方法
// def agent_selector() {

//     if (params.PublishPlatform.contains('Android') || params.PublishPlatform.contains('OpenHarmony')) {
//         // test new fairguard
//         // 159, 154
//         // online new fairguard
//         // 192.168.1.158
//         return env.JOB_NAME.contains('_test') ? 'WIN-192.168.1.159' : 'WIN-192.168.1.158'
//     } else if (params.PublishPlatform.contains('IOS') || params.PublishPlatform.contains('MAC')) {
//         // test new fairguard
//         // 250
//         // return env.JOB_NAME.contains('_test') ? 'MAC-192.168.3.127' : 'MAC-192.168.2.250'
//         return 'MAC-192.168.2.250'
//     } else {
//         echo("Could not get Agent from PublishPlatform: ${params.PublishPlatform} !")
//         error('Aborting Build.')
//     }

// }

// 2). 通用方法
def getLockResource() {
    // 机器, 项目英文名, 分支, 工作空间(项目根路径 + 自定义路径)
    return "${params.nodeName}_${ProjectEnglishName}_${params.PublishBranch}_${RootProjectPath}_${GitCustomDir}"
}

def getValueFromStr(String str, String key) {
    return str.split(',').find { it =~ "^${key}=" }?.split('=')[1]
}

def getHomePath() {
    if (params.PublishPlatform.contains('Android') || params.PublishPlatform.contains('OpenHarmony')) {
        return 'C:/Users/admin'
    } else {
        return '/Users/admin'
    }
}

def getUnityBuildTarget() {
    if (params.PublishPlatform == 'Android') {
        return 'Android'
    } else if (params.PublishPlatform == 'IOS') {
        return  'iOS'
    } else if (params.PublishPlatform == 'OpenHarmony') {
        return 'OpenHarmony'
    } else if (params.PublishPlatform == 'MAC') {
        return  'OSXUniversal'
    }
}

def replaceGameConfigTo(toPath) {
    withFileParameter(name:'configFile', allowNoFile: true) {
        sh """
            echo "\${configFile}"
            echo "step1"
            cat "\${configFile}"

            echo "step1 done"
            # echo "${configFile}"
            if [ ! -s "\${configFile}" ]; then
                echo "[自定义游戏配置文件]: 没有上传替换的游戏配置文件, 不进行任何操作"
            else
                echo '[自定义游戏配置文件]: 上传了替换的游戏配置文件, 进行覆盖操作'
                echo "配置文件目的路径: ${toPath}"
                echo "配置文件内容: \${configFile}"
                echo "step2"
                mkdir -p ${toPath}
                cp -rf \${configFile} ${toPath}/game_config.properties

                echo "step2 done"
            fi
        """
    }
}

def getUntiyPath() {
    def selectedVersion = PublishUnityVersion ? PublishUnityVersion : UnityVersion
    if (params.PublishPlatform == 'Android') {
        return "C:\\Gala\\Software\\Unity\\Unity${selectedVersion}\\Unity\\Editor\\Unity.exe"
    } else if (params.PublishPlatform == 'IOS' || params.PublishPlatform == 'MAC') {
        return "/Applications/Unity/Hub/Editor/${selectedVersion}/Unity.app/Contents/MacOS/Unity"
    } else if (params.PublishPlatform == 'OpenHarmony') {
        return "C:/Gala/Software/Unity/Tuanjie${selectedVersion}/Editor/Tuanjie.exe"
    }
}

def getEnvType() {
    return env.JOB_NAME.contains('_test') ? 'test' : 'online'
}

//--------------IOS方法定义START--------------------
//加固方法
def iOSBuildFunWithFairGuard(String iosBuildType) {
    env.IPA_NAME = "${IPA_NAME_PREFIX}_${iosBuildType}_fairguard_protected"
    fairguardArchivePath = "$GameProjectPath/IOSProject/BuildSuccess/*.xcarchive"
    //编译
    if (!Boolean.valueOf(env.isHasBuildArchive)) {
        sh """
            cd ${IosFairguardFTPToolPath}
            rm -rf $GameProjectPath/IOSProject/BuildSuccess/
            ./fairguardbuild_oss -p ${unitywork} -W
        """
        env.isHasBuildArchive = true
        copySymbols(fairguardArchivePath)
    }else {
        echo '不重复构建ARCHIVE'
    }
    //导包
    env.NEW_CMD_EXPORT_IPA = CMD_EXPORT_IPA.replace('replace_iosArchivePath', fairguardArchivePath).replace('replace_config', iosBuildType)
    println("NEW_CMD_EXPORT_IPA: $NEW_CMD_EXPORT_IPA")
    sh(returnStdout: false, script: 'eval $NEW_CMD_EXPORT_IPA')
    //拷贝
    sh(returnStdout: false, script: 'mkdir -p $PublishPathFinal')
    sh(returnStdout: false, script: 'cp $IPA_PATH/*.ipa $PublishPathFinal/${IPA_NAME}.ipa >>${m_log_path}')
}

//非加固方法
def iOSBuild(String iosBuildType) {
    //编译
    println("env.isHasBuildArchive: $env.isHasBuildArchive")
    if (!Boolean.valueOf(env.isHasBuildArchive)) {
        println("CMD_EXPORT_ARCHIVE: $CMD_EXPORT_ARCHIVE")
        sh(returnStdout: false, script: 'eval $CMD_EXPORT_ARCHIVE')
        env.isHasBuildArchive = true
        copySymbols(PATH_ARCHIVE)
    }else {
        echo '不重复构建ARCHIVE'
    }
    // 导包
    env.IPA_NAME = "${IPA_NAME_PREFIX}_${iosBuildType}"
    env.NEW_CMD_EXPORT_IPA = CMD_EXPORT_IPA.replace('replace_iosArchivePath', PATH_ARCHIVE).replace('replace_config', iosBuildType)
    println("NEW_CMD_EXPORT_IPA: $NEW_CMD_EXPORT_IPA")
    sh(returnStdout: false, script: 'eval $NEW_CMD_EXPORT_IPA')
    // 拷贝 ipa
    sh(returnStdout: false, script: 'mkdir -p $PublishPathFinal')
    sh(returnStdout: false, script: 'cp $IPA_PATH/*.ipa $PublishPathFinal/${IPA_NAME}.ipa >> ${m_log_path}')
}

def copySymbols(String pathArchive) {
    println("符号表(archive 文件)源路径: ${pathArchive}")
    // TODO: archive 文件是否有必要压缩成 zip 文件,
    // TODO: 验证 bugly 上传是否可以直接上传 archive.zip, 还是说需要传 dSYM.zip
    println("符号表拷贝目标路径: $PublishSymbolsPath")
    sh(returnStdout: false, script: 'mkdir -p ${PublishSymbolsPath}')
    sh(returnStdout: false, script: "cp -rf ${pathArchive} ${PublishSymbolsPath}")

    // ---------------------firebase 符号表上传---------------------
    println("firebase 符号表上传, 工具路径: ${GameProjectPath}/IOSProject/Pods/FirebaseCrashlytics/upload-symbols")
    if ( fileExists("${GameProjectPath}/IOSProject/Pods/FirebaseCrashlytics/upload-symbols")) {
        println("firebase 符号表上传, Info.plist路径: ${GameProjectPath}/IOSProject/GoogleService-Info.plist")
        println("firebase 符号表上传, 符号表目标路径: ${pathArchive}/dSYMs")
        sh(returnStdout: false, script: "${GameProjectPath}/IOSProject/Pods/FirebaseCrashlytics/upload-symbols -gsp ${GameProjectPath}/IOSProject/GoogleService-Info.plist -p ios ${pathArchive}/dSYMs")
    } else {
        println("firebase 符号表工具不存在, 无需上传")
    }
    // ---------------------firebase 符号表上传---------------------
}

def getGameGitPath(String envType) {
    if (JOB_NAME.contains('online')) {
        return "${env[RootProjectPath]}/${envType}/${params.PublishChannelType}/${PublishBranch}/${GitCustomDir}/"
    } else {
        return "${env[RootProjectPath]}/${envType}/${PublishBranch}/${GitCustomDir}/"
    }
}

//------------项目相关全局变量声明START-------------
// 2.脚本变量
// 2.1 通用部分
def CredentialsId = 'd1e3591d-93a1-4ba5-83e2-48a84810a241'
def JenkinsBuildPrefix = 'Jks'
// 2.2 自定义部分
def GameGitUrl = 'ssh://git@git.wckj.com:65501/double11/totalfootballproduction_2022_3.git'
def GameGitDefaultBranch = 'origin/master'//有些项目默认是main有些项目默认是master
def RenderGitUrl = 'ssh://git@git.wckj.com:65501/rendering/render_ump.git'

RootProjectPath = 'FootballNGProductionPath'
// 项目中文名, 用途: 1.ipa保存路径
def ProjectChineseName = '最佳球会'
// 项目英文名, 用途 1.阻塞队列 2.符号表保存路径
ProjectEnglishName = 'TotalFootball'
// 附加文件夹, 有些项目会默认再建一层拉取目录 比如新足球默认是 common
GitCustomDir = ''
// 项目 Unity 根目录, 有的项目Unity根目录与Git目录不在同一目录下，如果与git目录是同一目录留''即可
def GameDirName = 'FootballNGProduction'
// 加固工具Fairgurad
AndroidFairguardFTPToolPath = '//192.168.0.220/出包目录/Reinforce/FairGuard/Android_TotalFootball'
AndroidFairguardFTPToolName = 'FairGuard5.1.28-wangcheng-oss.jar'
AndroidFairguardLocalToolPath = '/C/Gala/Software/FairGuard/'
IosFairguardFTPToolPath = '/Volumes/出包目录/Reinforce/FairGuard/IOS_TotalFootball'
// 安卓
def AndroidProjectDirName = 'AndroidBuildOperableSoccer'
def AndroidPublishServerStringMap = '内网=Intranet,外网=Release,外网测试服=Extranet'
// 重签名 键：模糊匹配到包名称 值：精确匹配到重签的bat脚本
// TODO: 这里的格式没理解
def AndroidPublishChannelStringMap = '官网包=:official:*AndroidFormat*Normal,Garena沙箱参数包=:Garena:*AndroidFormat*Sandbox,Garena正式参数包=:Garena:*AndroidFormat*Production,Garena旧分支专用选项=:Garena:*AndroidFormat*,欧洲=:Google:*AndroidFormat*Gp,APKPURE=:Google:*AndroidFormat*Gp_apkpure,日本=:Google:*AndroidFormat*Gp_jp,香港韩国=:Google:*AndroidFormat*Gp_krhk,Taptap=:official:*AndroidFormat*Taptap,华为=:Huawei:*AndroidFormat*Normal,华为智慧屏=:Huawei:*AndroidFormat*Zh,华为云游戏评奖包=:Huawei:*AndroidFormat*Cloud,华为车机演示包=:Huawei:*AndroidFormat*Car,4399=:4399:*AndroidFormat*,阿里九游=:ali9you:*AndroidFormat*,好游快爆=:official:*AndroidFormat*Hykb,OPPO=:Oppo:*AndroidFormat*,VIVO=:Vivo:*AndroidFormat*,小米=:Xiaomi:*AndroidFormat*,应用宝=:Ysdk:*AndroidFormat*,233=:233:*AndroidFormat*,咪咕=:Migu:*AndroidFormat*Migu,咪咕大屏=:Migu:*AndroidFormat*Migu2,咪咕云化=:Migu:*AndroidFormat*MiguCloud,三星=:Samsung:*AndroidFormat*,百度=:baidu:*AndroidFormat*,摸摸鱼=:Momoyu:*AndroidFormat*,雷电=:LeiDian:*AndroidFormat*,抖音=:official:*AndroidFormat*Douyin,抖音2号包=:official:*AndroidFormat*Douyin2,抖音联运=:DouYin:*AndroidFormat*,先锋包=:official:*AndroidFormat*Xianfeng,B站=:Bilibili:*AndroidFormat*,雷电模拟器PC包=:official:*AndroidFormat*Ldpc,快手联运=:KuaiShou:*AndroidFormat*,荣耀=:Honor:*AndroidFormat*,斗鱼=:DouYu:*AndroidFormat*,国内比赛包=:official:*AndroidFormat*Championships,OneStore=:OneStore:*AndroidFormat*,海外三星=:OverseaSamsung:*AndroidFormat*'
// def signConfigs = 'Huawei_car=4096'

// IOS
def IosPublishServerStringMap = '内网=Intranet,外网=Online,外网测试服=Extranet'
def IosPublishChannelStringMap = '官网包=CN,Garena=Garena,新西兰=Newzealand,欧洲=EUR,先锋包=xianfeng,国内比赛包=Championships,韩国包=KOHK,日本包=JP'

// MAC
def MACPublishServerStringMap = '内网=Intranet,外网=Online,外网测试服=Extranet'
def MACPublishChannelStringMap = '官网包=CN'

// 引擎版本定义
// Unity版本, 用途 Android, iOS, Mac 使用
UnityVersion = '2021.3.32f1'
// 团结版本, 用途 Harmony 使用
TuanjieVersion = '2022.3.2t11'

// --------------------通用参数--------------------
// 步骤1. 定义参数数组, 填充通用参数
pipelineParameters = [
        // 平台选择参数选项 PublishPlatform
        choice(choices: ['Android', 'IOS', 'MAC', 'OpenHarmony'], description: '选择打包平台', name: 'PublishPlatform'),
        // Unity代码分支选项 PublishBranch
        gitParameter(branch: '', branchFilter: '', defaultValue: "${GameGitDefaultBranch}", description: '代码分支,选择分之后会自动丢弃所有改动,并拉取到对应分支,默认master', name: 'PublishBranch', quickFilterEnabled: true, selectedValue: 'NONE', sortMode: 'DESCENDING_SMART', tagFilter: '*', type: 'GitParameterDefinition', useRepository: "${GameGitUrl}"),
        // 渲染分支选项 RenderPublishBranch
        gitParameter(branch: '', branchFilter: '', defaultValue: 'origin/master', description: 'RenderBase 代码分支名称,会自动丢弃RenderBase本地所有改动,并拉取到对应分支,默认master', name: 'RenderPublishBranch', quickFilterEnabled: true, selectedValue: 'NONE', sortMode: 'DESCENDING_SMART', tagFilter: '*', type: 'GitParameterDefinition', useRepository: "${RenderGitUrl}"),
        // 根据PublishPlatform参数切换Channel选项 PublishChannelType
        [$class: 'CascadeChoiceParameter', choiceType: 'PT_SINGLE_SELECT', filterLength: 1, filterable: false, name: 'PublishChannelType', randomName: 'choice-parameter-14274211920415442', referencedParameters: 'PublishPlatform', script: [$class: 'GroovyScript', fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: 'return["ERROR"]'], script: [classpath: [], oldScript: '', sandbox: true, script:
                '''
    switch(PublishPlatform) {
        case "Android":
            return ["Intranational","Overseas","Overseas_JP","Overseas_KR","Garena","Simulator"]
        case "IOS":
            return ["Intranational","Overseas","Overseas_JP","Overseas_KR","Garena","Simulator"]
        case "OpenHarmony":
            return ["Intranational"]
        case "MAC":
            return ["Intranational"]
    }
    '''
        ]]],
        choice(choices: ['2021.3.32f1'], description: '选择编译使用的Unity版本', name: 'PublishUnityVersion'),

        // PublishWithUnity选项
        booleanParam(defaultValue: true, description: '是否编译unity,默认会编译unity', name: 'PublishWithUnity'),
        // BuildWithPlatform选项
        booleanParam(defaultValue: true, description: '是否编译原生项目', name: 'BuildWithPlatform'),

        // 根据PublishPlatform选项显示的打包渠道 PublishChannel
        [$class: 'CascadeChoiceParameter', choiceType: 'PT_CHECKBOX', description: '''可选择单个或者多个渠道一起打包(海外渠道只支持单个打包,请勿多选)<br>PS:先锋服包必须勾选外网测试''', filterLength: 1, filterable: false, name: 'PublishChannel', randomName: 'choice-parameter-14274211925563964', referencedParameters: 'PublishPlatform,PublishChannelType', script: [$class: 'GroovyScript', fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: 'return["ERROR"]'], script: [classpath: [], oldScript: '', sandbox: true, script:
                '''
    switch(PublishPlatform) {
        case "Android":
            switch(PublishChannelType) {
                case "Intranational":
                    return ["官网包:selected","Taptap","华为","华为智慧屏","华为云游戏评奖包","华为车机演示包","4399","阿里九游","好游快爆","OPPO","VIVO","小米","应用宝","233","咪咕","咪咕大屏","咪咕云化","三星","百度","摸摸鱼","雷电","抖音","抖音2号包","抖音联运","先锋包","B站","快手联运","荣耀","斗鱼","国内比赛包"]
                case "Overseas":
                    return ["欧洲:selected","APKPURE"]
                case "Overseas_JP":
                    return ["日本:selected"]
                case "Overseas_KR":
                    return ["香港韩国:selected","OneStore","海外三星"]
                case "Garena":
                    return ["Garena沙箱参数包:selected","Garena正式参数包"]
                case "Simulator":
                    return ["雷电模拟器PC包:selected"]
            }
            break;
        case "IOS":
            switch(PublishChannelType) {
                case "Intranational":
                    return ["官网包:selected","先锋包","国内比赛包"]
                case "Overseas":
                    return ["欧洲:selected","新西兰"]
                case "Overseas_JP":
                    return ["日本包:selected"]
                case "Overseas_KR":
                    return ["韩国包:selected"]
                case "Garena":
                    return ["Garena:selected"]
            }
            break;
        case "MAC":
            return ["官网包:selected"]
        case "OpenHarmony":
            return ["华为:selected"]
    }
    '''
        ]]],

        // 根据PublishPlatform选项显示的出包类型  PublishUnityProjectType
        [$class: 'CascadeChoiceParameter', choiceType: 'PT_SINGLE_SELECT', description: '''出包类型(ios需关注,android不用管!)<br>debug:测试包(测试设备可以安装)<br>release发布包(测试设备无法安装,但上传苹果需要传release)<br>both两者都打(提审的时候打both,测debug上传release)''', filterLength: 1, filterable: false, name: 'PublishProductType', randomName: 'choice-parameter-14274211927728339', referencedParameters: 'PublishPlatform', script: [$class: 'GroovyScript', fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: 'return["ERROR"]'], script: [classpath: [], oldScript: '', sandbox: true, script:
                '''
    switch(PublishPlatform) {
        case "Android":
            return ["apk","aab"]
        case "IOS":
            return ["debug","release","both"]
        case "OpenHarmony":
            return ["hap","app"]
        case "MAC":
            return ["debug","release","both"]
    }
    '''
        ]]],
        [$class: 'CascadeChoiceParameter', choiceType: 'PT_SINGLE_SELECT', description: '''Mono:发布Mono32位工程,国内包请选择该选项<br>Il2cpp:发布Il2cpp工程(64位和32位),海外包整包请选择该选项<br>Il2cpp_OBB:发布Il2cpp(OBB分包)工程(64位和32位),海外包分包请选择该选项<br>None:不发布工程直接打android apk,无unity修改时可选择该选项<br>None_OBB:不发布工程直接打android apk,无unity修改时(海外分包情况下)可选择该选项<br>PS:国内暂不开放Il2cpp选项,海外暂不开放Mono选项<br>''', filterLength: 1, filterable: false, name: 'PublishUnityProjectType', randomName: 'choice-parameter-14274211929832349', referencedParameters: 'PublishPlatform,PublishProductType', script: [$class: 'GroovyScript', fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: 'return["ERROR"]'], script: [classpath: [], oldScript: '', sandbox: true, script:
                '''
    switch(PublishPlatform) {
        case "Android":
            if(PublishProductType.equals("aab")) {
                return ["Il2cpp_AAB","None"]
            } else {
                return ["Il2cpp","Il2cpp_AAB","Mono","None"]
            }
        case "IOS":
            return ["Il2cpp","Mono","None"]
        case "MAC":
            return ["Il2cpp","Mono","None"]
        case "OpenHarmony":
            return ["Il2cpp"]
    }
    '''
        ]]],
        [$class: 'CascadeChoiceParameter', choiceType: 'PT_SINGLE_SELECT', description: '选择发布工程的位数', filterLength: 1, filterable: false, name: 'PublishUnityProjectTypeBit', randomName: 'choice-parameter-14274211931939256', referencedParameters: 'PublishUnityProjectType,PublishPlatform', script: [$class: 'GroovyScript', fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: 'return["ERROR"]'], script: [classpath: [], oldScript: '', sandbox: true, script:
                '''
    if(PublishUnityProjectType.equals("Mono")) {
        return ["打包32位"]
    } else if(PublishUnityProjectType.equals("Il2cpp") || PublishUnityProjectType.equals("Il2cpp_AAB")) {
        return ["打包32和64位","打包64位","打包32位"]
    } else {
        return ["无"]
    }
    '''
        ]]],
        persistentString(defaultValue: '0.0.1', description: '出包版本', name: 'PublishVersionName', successfulOnly: false, trim: false),
        persistentString(defaultValue: '10', description: '', name: 'PublishVersionCode', successfulOnly: true, trim: true),
        choice(choices: ['内网', '外网', '外网测试服'], description: '''选择后会使用内网或正式的配置,打完包后会分别上传至不同的共享文件夹<br>外网=提审<br>外网测试服=gameid为负数的测试服''', name: 'PublishServer'),
        persistentString(defaultValue: '日常测试包', description: '''发布到指定目录（不填写 不会拷贝）<br>PS:只有android生效!!!!''', name: 'PublishReleasePath', successfulOnly: false, trim: false),
        choice(choices: ['Normal', 'Development'], description: 'Development模式', name: 'BuildInDevelopmentMode'),
        [$class: 'CascadeChoiceParameter', choiceType: 'PT_CHECKBOX', filterLength: 1, filterable: false, name: 'DevelopmentModeParm', randomName: 'choice-parameter-14274211937999000', referencedParameters: 'BuildInDevelopmentMode', script: [$class: 'GroovyScript', fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: ''], script: [classpath: [], oldScript: '', sandbox: true, script:
                '''
    if(BuildInDevelopmentMode.equals("Development")) {
        return ["EnableDeepProfilingSupport", "AndroidDebug"]
    } else {
        return ["无"]
    }
    '''
        ]]],
        booleanParam(description: '', name: 'ILRuntime'),
        booleanParam(defaultValue: true, description: '', name: 'HybridCLR'),
        booleanParam(description: '正式发布的场景开关', name: 'ReleaseScenes'),
        booleanParam(description: '加固开关（test: 默认关, online: 默认开）', name: 'PublishReinforce', defaultValue: JOB_NAME.contains('online')),
        booleanParam(description: 'DeepProfiling模式', name: 'Deep_Profiling_Support'),
        booleanParam(description: '', name: 'SHOW_FPS_TEMPERATURE'),
        booleanParam(description: '', name: 'UseFrameRate60'),
        booleanParam(description: '', name: 'UsePvpRecord'),
        booleanParam(description: '', name: 'UsePvpRandomInput'),
        booleanParam(description: '', name: 'iOSCoreML'),
        booleanParam(description: '', name: 'isIOSShowNNModelDebug'),
        booleanParam(description: '', name: 'PvpTestSync'),
        choice(choices: ['NORMAL', 'MIDDLE', 'MINI'], description: '是否小包（谷歌包）, 中包模式', name: 'ADDRESSABLE_TYPE'),
        booleanParam(description: 'ADDRESSABLE_COPYBIN', name: 'ADDRESSABLE_COPYBIN'),
        choice(choices: ['CN', 'EN', 'ALL'], description: '', name: 'LanguageType'),
        booleanParam(description: '是否显示虚拟光标', name: 'VIRTUAL_CURSOR'),
        booleanParam(description: '咪咕世界杯选项', name: 'isMiguWroldcup'),
        booleanParam(description: '', name: 'isMiguWroldcupTest'),
        booleanParam(description: '', name: 'ReimportShader'),
        choice(choices: ['Chinese', 'SimpleChinese', 'Arabic', 'English', 'SimpleEnglish', 'Indonesia', 'Portuguese', 'Cantonese', 'Cantonese', 'Japanese', 'Korean', 'Turkish', 'SimpleCantonese&Korean'], description: '解说语音', name: 'CommentatingType'),
        booleanParam(description: '云测试选项', name: 'CloudTest'),
        [$class: 'CascadeChoiceParameter', choiceType: 'PT_CHECKBOX', filterLength: 1, filterable: false, name: 'useJDK11', randomName: 'choice-parameter-14274211937999000', referencedParameters: 'PublishChannelType', script: [$class: 'GroovyScript', fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: ''], script: [classpath: [], oldScript: '', sandbox: true, script:
                '''
    if(PublishChannelType.equals("Garena")) {
        return ["true"]
    } else {
        return ["false"]
    }
    '''
        ]]],

        booleanParam(description: '', name: 'isAstcPkg'),
        booleanParam(description: '', name: 'saveAstcSetting'),


        base64File(description: '自定义打包配置,如果提供,则以此配置文件为准(如果选择了自定义配置文件,内网外网的选择就只会影响到最后包保存的位置)', name: 'configFile'),
        booleanParam(description: '上传符号表至bugly', name: 'upload_symbols_to_bugly'),
]

// --------------------自定义参数--------------------
// 步骤2. 更新参数数组, 拼接自定义参数
updateCustomParameters()

def updateCustomParameters() {
    echo "JOB_NAME: ${JOB_NAME}"
    if (JOB_NAME.contains('online')) {

        // 平台选择参数选项 PublishPlatform
        pipelineParameters.add(0, choice(choices: ['WIN-192.168.1.158', 'MAC-192.168.2.250'], description: '选择打包机器', name: 'nodeName'))
        onlineParams = []
        pipelineParameters.addAll(onlineParams)
    } else if (JOB_NAME.contains('test')) {
        pipelineParameters.add(0, choice(choices: ['WIN-192.168.1.159', 'WIN-192.168.1.154', 'MAC-192.168.2.250'], description: '选择打包机器', name: 'nodeName'))
        testParams = [
                persistentString(defaultValue: '', description: 'BuildWithPlatform不勾选的情况下生效,暂时只有ios逻辑,不填写默认往渲染机器上拷贝,目录为/Users/xuanran/xcodeProject/项目名/jenkins编号', name: 'PlatformProjectCopy', successfulOnly: false, trim: false),
                booleanParam(description: '集成UWA插件', name: 'UWA'),
                booleanParam(description: '爆框测试', name: 'PocoSDKBurst'),
                booleanParam(description: '勾选上不开启引擎DLL热更，加快打包速度', name: 'NO_DHE'),
                booleanParam(description: '', name: 'DisableWriteTypeTree'),
        ]
        pipelineParameters.addAll(testParams)
    } else {
        echo "未匹配到特定的JOB_NAME, 不添加参数"
    }
}

// 步骤3. 设置下一次使用的选项
// jenkins构建选项设置
properties([
        parameters(pipelineParameters)
])


// start of pipeline
pipeline {
    agent { node { label "${params.nodeName}" } }
    options {
        timestamps()
        lock resource: getLockResource() // 机器, 项目英文名, 分支, 工作空间(项目根路径 + 自定义路径)
    }
    environment {
        HOME = getHomePath()
        LANG = 'en_US.UTF-8'
        EnvType = getEnvType()
        GameGitPath = getGameGitPath("${EnvType}")

        GameProjectPath = "${env.GameGitPath}/${GameDirName}"
    }
    stages {
        stage('git拉取') {
            steps {
                script {
                    GameGitPath = env.GameGitPath
                    GameProjectPath = env.GameProjectPath
                }
                checkout([
                        $class: 'GitSCM',
                        branches: [[name: "${params.PublishBranch}"]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [[$class: 'CloneOption', depth: 0, timeout: 30], [$class: 'CheckoutOption', timeout: 30], [$class: 'RelativeTargetDirectory', relativeTargetDir: "${GameGitPath}"], [$class: 'LocalBranch', localBranch: ''], ],
                        submoduleCfg: [],
                        userRemoteConfigs: [[url: "${GameGitUrl}", credentialsId: "${CredentialsId}"]]
                ])
                checkout([
                        $class: 'GitSCM',
                        branches: [[name: "${params.RenderPublishBranch}"]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [[$class: 'CloneOption', depth: 0, timeout: 30], [$class: 'CheckoutOption', timeout: 30], [$class: 'RelativeTargetDirectory', relativeTargetDir: "${GameProjectPath}/Assets/RenderUMP"], [$class: 'LocalBranch', localBranch: ''], ],
                        submoduleCfg: [],
                        userRemoteConfigs: [[url: "${RenderGitUrl}", credentialsId: "${CredentialsId}"]]
                ])
                dir("${GameGitPath}") {
                    script {
                        env.GitShortHead = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    }
                }
            }
        }
        stage('参数预处理') {
            steps {
                script {
                    // 打印当前打包配置
                    echo "envType:${env.EnvType}"
                    echo "RootProjectPath:${env[RootProjectPath]}"
                    echo "GameGitPath:${GameGitPath}"
                    echo "GameProjectPath:${GameProjectPath}"
                    echo "NODE_NAME:${NODE_NAME}"
                    // 通用参数
                    JobType = env.JOB_NAME.contains('_test') ? '测试' : '正式'

                    wrap([$class: 'BuildUser']) {
                        env.BuildUser = "${env.BUILD_USER}"
                    }
                    // Android 参数
                    AndroidGradlePath = "${GameProjectPath}/${AndroidProjectDirName}"
                }
                script {
                    // 磁盘预警逻辑
                    sh """
                    # 磁盘预警
                    echo "磁盘预警START"
                    # 将 $NODE_NAME 转换为小写
                    lowercase_nodes=\$(echo "$NODE_NAME" | tr '[:upper:]' '[:lower:]')
                    echo "lowercase_nodes: \$lowercase_nodes"

                    
                    if [[ \$lowercase_nodes == mac* ]]; then
                        echo "NODE_NAME starts with 'mac' (case-insensitive)."
                        ShellPath='/Volumes/出包目录/Jenkins缓存/脚本/磁盘预警.sh'
                    else
                        echo "NODE_NAME does not start with 'mac' (case-insensitive)."
                        ShellPath='\\\\192.168.0.220\\出包目录\\Jenkins缓存\\脚本\\磁盘预警.sh'
                    fi
                    sh -xe "\$ShellPath" "${ProjectEnglishName}" "${PublishPlatform}" "${GameProjectPath}" # 使用引号以防参数中有空格
                    echo "磁盘预警END"
                    """
                }
                dir("${GameProjectPath}") {
                    script {
                        // 参数预处理-通用部分
                        currentBuild.displayName = "#${BUILD_NUMBER} Ver:${PublishVersionName}-${PublishPlatform}-${PublishChannelType}"
                        currentBuild.description = """
                            机器: ${NODE_NAME}<br>
                            平台: ${params.PublishPlatform}<br>
                            代码分支: ${params.PublishBranch}<br>
                            渲染分支: ${params.RenderPublishBranch}<br>
                            地区: ${params.PublishChannelType}<br>
                            整包版本: ${params.PublishVersionName}<br>
                            加固: ${params.PublishReinforce}<br>
                            构建人: ${BuildUser}<br>
                        """

                        def customStr = ''
                        if (params.BuildInDevelopmentMode == 'Development') {
                            echo '添加DevelopmentMode区分'
                            customStr += '_DevelopmentMode'
                        }
                        if (params.UWA) {
                            echo '添加UWA区分'
                            customStr += '_UWA'
                        }

                        if (params.PublishReinforce) {
                            PublishReinforceHint = '加固'
                        } else {
                            PublishReinforceHint = '非加固'
                        }

                        if (params.PublishPlatform == 'Android' || params.PublishPlatform == 'OpenHarmony') {
                            // Android, 鸿蒙
                            echoType = 'echo'
                            env.PublishPathStart = "//192.168.0.220/出包目录/${ProjectChineseName}/${JobType}/${PublishReinforceHint}/${PublishPlatform}/"
                            echo 'env.PublishPathStart:' + env.PublishPathStart
                            env.PublishSymbolsPath = "//192.168.0.220/出包目录/符号表/${ProjectEnglishName}/Android/${PublishBranch.replaceAll('/', '_')}/${PublishVersionName}/${JenkinsBuildPrefix}${BUILD_ID}_${env.GitShortHead}"
                            echo 'env.PublishSymbolsPath:' + env.PublishSymbolsPath
                        } else if (params.PublishPlatform == 'IOS' || params.PublishPlatform == 'MAC') {
                            // iOS, Mac
                            echoType = '/bin/echo'
                            env.PublishPathStart = "/Volumes/出包目录/${ProjectChineseName}/${JobType}/${PublishReinforceHint}/${PublishPlatform}/"
                            env.PublishSymbolsPath = "/Volumes/出包目录/符号表/${ProjectEnglishName}/IOS/${PublishBranch.replaceAll('/', '_')}/$PublishVersionName/${JenkinsBuildPrefix}${BUILD_ID}_${env.GitShortHead}"
                        } else {
                            // 其他
                        }

                        //
                        if (params.ReleaseScenes) {
                            echo '添加ReleaseScenes区分'
                            customStr += '_ReleaseScenes'
                        }

                        if (params.PvpTestSync) {
                            echo '添加PvpTestSync区分'
                            customStr += '_PvpTestSync'
                        }

                        // TODO: 后续把该参数整理到 git 拉取部分
                        String branchName = PublishBranch.replaceFirst('origin/', '').replaceAll('/', '_')

                        env.ProductParm4Name = "${JenkinsBuildPrefix}${BUILD_ID}_${branchName}_${env.GitShortHead}${customStr}"
                        echo 'ProductParm4Name:' + env.ProductParm4Name
                        env.PublishPathFinal = "${PublishPathStart}${PublishReleasePath}"
                        echo 'PublishPathFinal:' + env.PublishPathFinal

                        if (params.PublishPlatform == 'Android') {
                            // 参数预处理-Android部分

                            //如果需要加固，原始包会先拷贝到这个目录下
                            env.PublishReinforceTempPath = "${PublishPathFinal}/AndroidTemp/${BUILD_NUMBER}"
                            echo "PublishReinforceTempPath: ${PublishReinforceTempPath}"
                            // echo "PublishReinforceTempPath处理后:"+PublishReinforceTempPath.replace('\\','/')

                            def androidPackageFormat = params.PublishProductType == 'aab' ? 'bundle' : 'assemble'
                            echo "androidPackageFormat: ${androidPackageFormat}"

                            def publishServerString = (params.BuildInDevelopmentMode == 'Development' && params.DevelopmentModeParm.contains('AndroidDebug')) ? 'Debug' : getValueFromStr(AndroidPublishServerStringMap, PublishServer)
                            echo "publishServerString: ${publishServerString}"

                            String AndroidPackageParamTemp = ''

                            def array = PublishChannel.split(',')
                            echo "array: ${array}"
                            array.eachWithIndex { channel, index ->
                                echo "${index}=>${channel}"

                                AndroidPackageParamTemp += getValueFromStr(AndroidPublishChannelStringMap, channel) + publishServerString + ' '
                                echo 'AndroidPackageParamTemp-->' + AndroidPackageParamTemp
                            }
                            env.AndroidPackageParam = "${AndroidPackageParamTemp}".replaceAll('\\*AndroidFormat\\*', androidPackageFormat)
                            echo 'AndroidPackageParam-->' + env.AndroidPackageParam
                        } else if (params.PublishPlatform == 'IOS') {
                            // 参数预处理-iOS,
                            env.IOSConfigpath = getValueFromStr(IosPublishServerStringMap, PublishServer)
                            echo "IOSConfigpath-->${IOSConfigpath}"
                            echo "PublishChannel-->${PublishChannel}"

                            env.IOSIpaChannelName = getValueFromStr(IosPublishChannelStringMap, PublishChannel)
                            echo "IOSIpaChannelName-->${IOSIpaChannelName}"
                        } else if (params.PublishPlatform == 'OpenHarmony') {
                            // 参数预处理-鸿蒙部分
                        } else if (params.PublishPlatform == 'MAC') {
                            env.MACConfigpath = getValueFromStr(MACPublishServerStringMap, PublishServer)
                            echo "MACConfigpath-->${MACConfigpath}"

                            env.MACChannelName = getValueFromStr(MACPublishChannelStringMap, PublishChannel)
                            echo "MACChannelName-->${MACChannelName}"
                        } else {
                            // 参数预处理-其他
                        }

                        def calculateVersionCode = """
                        # 验证版本号格式是否正确
                        if ! [[ $PublishVersionName =~ ^[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\$ ]]; then
                            echo "错误：版本号格式不正确，请输入类似1.4.501的格式，且每个小数点之间的数字不超过3个。"
                            exit 1
                        fi

                        # 将版本号中的点号替换为空格，然后分割成数组
                        IFS='.' read -r -a version_array <<< "$PublishVersionName"

                        # 将数组元素拼接成字符串，并在每个元素前面补足0到3位
                        padded_version=""
                        for part in "\${version_array[@]}"; do
                            padded_version="\${padded_version}\$(printf "%03d" "\$((10#\$part))")"
                        done

                        # 去掉开头的0
                        final_version=\$(echo "\$padded_version" | sed 's/^0*//')

                        #echo "原始版本号: $PublishVersionName"
                        #echo "转换后版本号: \$final_version"

                        echo \$final_version
                        """
                        env.PublishVersionCode = sh(script: calculateVersionCode, returnStdout: true).trim()
                        println "PublishVersionCode:${PublishVersionCode}"
                    }
                }
            }
        }
        stage('unity编译') {
            when {
                expression { return params.PublishWithUnity }
            }
            steps {
                dir("${GameProjectPath}") {
                    script {
                        sh(returnStdout: true, script: 'rm -rf ${GameProjectPath}/Assets/Scripts/Main/ILRuntime/Generated')
                        // 操作足球特有逻辑
                        // 用于补充宏控制的兼容
                        sh(returnStdout: true, script: "ex -sc '1i|#define BUILD_TOTAL_FOOTBALL' -cx '${GameProjectPath}/Assets/GamePlay/Scripts/Modules/Game/GameManager.cs'")

                        def PublishUnityProjectCustomParameters = ''
                        // Jenkins 参数
                        // 通用部分
                        PublishUnityProjectCustomParameters += "BuildInDevelopmentMode:${BuildInDevelopmentMode}"
                        PublishUnityProjectCustomParameters += "^NODE:${NODE_NAME}"
                        PublishUnityProjectCustomParameters += "^PublishJenkinsJob:${JOB_NAME}"
                        PublishUnityProjectCustomParameters += "^PublishJenkinsBuildNumber:${BUILD_NUMBER}"

                        // 项目自定义部分
                        // 旧方式
                        if (params.DevelopmentModeParm.contains('EnableDeepProfilingSupport')) {
                            PublishUnityProjectCustomParameters += '^BuildInDeepProfilingMode:DeepProfiling'
                        } else {
                            PublishUnityProjectCustomParameters += '^BuildInDeepProfilingMode:Normal'
                        }
                        // 新方式
                        PublishUnityProjectCustomParameters += "^DevelopmentModeParm:${DevelopmentModeParm}"

                        PublishUnityProjectCustomParameters += "^SHOW_FPS_TEMPERATURE:${SHOW_FPS_TEMPERATURE}"
                        PublishUnityProjectCustomParameters += "^PublishChannelType:${PublishChannelType}"
                        PublishUnityProjectCustomParameters += "^PublishBranch:${PublishBranch}"
                        PublishUnityProjectCustomParameters += "^PublishVersionName:${PublishVersionName}"
                        PublishUnityProjectCustomParameters += "^UseFrameRate60:${UseFrameRate60}"
                        PublishUnityProjectCustomParameters += "^ReleaseScenes:${ReleaseScenes}"
                        PublishUnityProjectCustomParameters += "^UsePvpRecord:${UsePvpRecord}"
                        PublishUnityProjectCustomParameters += "^UsePvpRandomInput:${UsePvpRandomInput}"

                        PublishUnityProjectCustomParameters += "^ILRuntime:${ILRuntime}"
                        PublishUnityProjectCustomParameters += "^HybridCLR:${HybridCLR}"
                        PublishUnityProjectCustomParameters += "^ADDRESSABLE_TYPE:${ADDRESSABLE_TYPE}"
                        PublishUnityProjectCustomParameters += "^GitShortHead:${GitShortHead}"
                        PublishUnityProjectCustomParameters += "^PvpTestSync:${PvpTestSync}"
                        PublishUnityProjectCustomParameters += "^PublishVersionCode:${PublishVersionCode}"
                        PublishUnityProjectCustomParameters += "^PublishChannel:${PublishChannel}"
                        PublishUnityProjectCustomParameters += "^PublishServer:${PublishServer}"
                        PublishUnityProjectCustomParameters += "^ADDRESSABLE_COPYBIN:${ADDRESSABLE_COPYBIN}"
                        PublishUnityProjectCustomParameters += "^LanguageType:${LanguageType}"
                        PublishUnityProjectCustomParameters += "^VIRTUAL_CURSOR:${VIRTUAL_CURSOR}"
                        PublishUnityProjectCustomParameters += "^isMiguWroldcup:${isMiguWroldcup}"
                        PublishUnityProjectCustomParameters += "^isMiguWroldcupTest:${isMiguWroldcupTest}"
                        PublishUnityProjectCustomParameters += "^isAstcPkg:${isAstcPkg}"
                        PublishUnityProjectCustomParameters += "^saveAstcSetting:${saveAstcSetting}"
                        PublishUnityProjectCustomParameters += "^ReimportShader:${ReimportShader}"
                        PublishUnityProjectCustomParameters += "^CommentatingType:${CommentatingType}"
                        PublishUnityProjectCustomParameters += "^AceBuild:${PublishReinforce}"
                        if (JOB_NAME.contains('test')) {
                            PublishUnityProjectCustomParameters += "^PocoSDKBurst:${PocoSDKBurst}"
                            PublishUnityProjectCustomParameters += "^DisableWriteTypeTree:${DisableWriteTypeTree}"
                            PublishUnityProjectCustomParameters += "^NO_DHE:${NO_DHE}"
                            PublishUnityProjectCustomParameters += "^UWA:${UWA}"
                        }

                        PublishUnityProjectCustomParameters += "^PublishReinforce:${PublishReinforce}"

                        if (params.PublishPlatform == 'IOS') {
                            PublishUnityProjectCustomParameters += "^iOSCoreML:${iOSCoreML}"
                            PublishUnityProjectCustomParameters += "^isIOSShowNNModelDebug:${isIOSShowNNModelDebug}"
                        }
                        if (params.PublishPlatform == 'OpenHarmony') {
                            PublishUnityProjectCustomParameters += '^isExportProject:true'
                        }

                        // ScenesType 没有设置选项, 所以没有传值到 Unity
                        // if (params.ScenesType == 'custom') {
                        //     if (params.CustomScenes == '') {
                        //         PublishUnityProjectCustomParameters += "^ScenesChoose: "
                        //     } else {
                        //         PublishUnityProjectCustomParameters += "^ScenesChoose:${CustomScenes}"
                        //     }
                        // } else if (params.ScenesType == 'default') {
                        //     PublishUnityProjectCustomParameters += "^ScenesChoose: "
                        // } else {
                        //     PublishUnityProjectCustomParameters += "^ScenesChoose:${ScenesType}"
                        // }
                        def implatform = ''
                        if (!PublishBranch.contains('master')) {
                            if (PublishChannelType.startsWith('Overseas') || PublishChannelType == 'Garena') {
                                implatform = 'YouMe'
                            } else if (PublishChannelType == 'Intranational' || PublishChannelType == 'Simulator') {
                                implatform = 'Tencent'
                            }
                        }
                        PublishUnityProjectCustomParameters += "^IMPlatform:${implatform}"

                        env.PublishUnityProjectCustomParameters = PublishUnityProjectCustomParameters

                        env.UnityBuildTarget = getUnityBuildTarget()
                        env.UnityPath = getUntiyPath()

                        // 处理解说导入Unity打开慢，提前处理
                        if (CommentatingType != 'Chinese') {
                            echo '解说非Chinese，在未打开Unity前删除Chinese文件夹'
                            sh(returnStdout: false, script: "rm -rf  ${GameProjectPath}/Assets/LoadResources/Audio/Commentating/Chinese")
                        }

                        def cmd = "${UnityPath} -quit -batchmode -nographics -buildTarget ${UnityBuildTarget} -projectPath ${GameProjectPath} -logFile -executeMethod PublishProjectWindows.BuildFromJenkins PublishUnityPlatform{=}${PublishPlatform} PublishUnityProjectType{=}${PublishUnityProjectType} PublishChannel{=}${PublishChannel} PublishUnityProjectTypeBit{=}${PublishUnityProjectTypeBit} \'PublishUnityProjectCustomParameters{=}${PublishUnityProjectCustomParameters}\'"


                        if (env.JOB_NAME.contains('_online')) {
                            cmd += ' -cacheServerEnableDownload false -cacheServerEnableUpload false'
                        }

                        cmd = cmd.replace('\\', '\\\\')
                        println "unity_runcmd:${cmd}"
                        //sh(returnStdout: false, script: '${UnityPath} -quit -batchmode -nographics -buildTarget ${UnityBuildTarget} -projectPath ${GameProjectPath} -logFile ${GameProjectPath}/Jenkins_${JOB_NAME}.txt -executeMethod PublishProjectWindows.BuildFromJenkins PublishUnityPlatform{=}${PublishPlatform} PublishUnityProjectType{=}${PublishUnityProjectType} PublishChannel{=}${PublishChannel} PublishUnityProjectTypeBit{=}${PublishUnityProjectTypeBit} PublishUnityProjectCustomParameters{=}${PublishUnityProjectCustomParameters}')
                        sh(returnStdout: false, script: "${cmd}")
                    }
                }
            }
        }
        stage('android编译') {
            when {
                expression { params.BuildWithPlatform && params.PublishPlatform == 'Android' }
            }
            steps {
                dir("${AndroidGradlePath}") {
                    script {
                        if (params.PublishReinforce) {
                            sh """

                            if [ -d ${PublishReinforceTempPath} -a "`ls -A ${PublishReinforceTempPath}`" != "" ]; then
                                    echo "${PublishReinforceTempPath} 不为空!"
                                    rm -rf ${PublishReinforceTempPath}
                                else
                                    echo "${PublishReinforceTempPath} 为空!"
                            fi

                            """
                            androidPublishPath = PublishReinforceTempPath
                        } else {
                            println '不执行加固'
                            androidPublishPath = PublishPathFinal
                        }

                        replaceGameConfigTo("${AndroidGradlePath}/files/config/customize".replace('\\', '\\\\'))

                        sh(returnStdout: false, script: "${AndroidGradlePath}/gradlew.bat clean".replace('\\', '\\\\'))

                        def runcmd = "${AndroidGradlePath}/gradlew.bat ${AndroidPackageParam} -PJVersionName=${PublishVersionName} -PJVersionCode=${PublishVersionCode} -PJReleasePath=${androidPublishPath} -PJProjectEnglishName=${ProjectEnglishName} -PJProductParm4Name=${ProductParm4Name} -PJReinforce=${PublishReinforce} -PJAceBuild=${PublishReinforce} -PJSymbolsSavePath=${PublishSymbolsPath} -Dorg.gradle.java.home=${JDK11} ".replace('\\', '\\\\')
                        println "android_runcmd:${runcmd}"
                        sh(returnStdout: false, script: runcmd)

                        //打完包判断是否加固
                        if (params.PublishReinforce) {
                            // 安卓加固，走shell操作文件
                            // def encryptedWithFairGuard {
                            echo '走安卓加固逻辑'

                            PublishReinforceTempPath = PublishReinforceTempPath.replace('\\', '/')
                            PublishPathFinal = PublishPathFinal.replace('\\', '/')
                            sh """
                            runFairGuard() {
                                # 参数检查
                                if [ "\$#" -ne 2 ]; then
                                    echo "Usage: runFairGuard <file_path> <number>"
                                    return 1
                                fi

                                local originalFilePath=\$1
                                local number=\$2
                                local originalDir=\$(dirname "\$originalFilePath")
                                local originalFileName=\$(basename "\$originalFilePath")
                                local originalfileNameWithoutExtension=\${originalFileName%.*}
                                local fileExtension="\${originalFileName##*.}"
                                local newFilePath="\${originalDir}/temp/\${number}.\${fileExtension}"
                                local tempReinforceFilePath="\${originalDir}/temp/\${number}_fairguard_protected.\${fileExtension}"
                                local originalReinforceFilePath="\${originalDir}/\${originalfileNameWithoutExtension}_fairguard_protected.\${fileExtension}"

                                #临时文件夹
                                mkdir -p "\${originalDir}/temp"
                                # 重命名文件为指定的序号.apk
                                mv "\$originalFilePath" "\$newFilePath"

                                # 检查是否成功重命名
                                if [ ! -f "\$newFilePath" ]; then
                                    echo "Error: Failed to rename the file."
                                    return 1
                                fi

                                # 进入 FairGuard 工具的目录
                                cd \${fairguardToolLocalPath} || return

                                # 根据文件类型设置签名开关
                                local signSwitch=""
                                if [[ "\$newFilePath" == *.aab ]]; then
                                    echo "The file path ends with .aab"
                                else
                                    echo "The file path does not end with .aab"
                                    signSwitch="-signapk"
                                fi

                                # 构建 FairGuard 命令
                                cmdNewFilePath="\${newFilePath//\\/\\\\}"
                                local cmd1="java -jar $AndroidFairguardFTPToolName -autoconfig -inputfile \$cmdNewFilePath -keystorePath \${fairguardToolLocalPath}/key/limboworks.keystore -alias limboworks -password 123456 -aliasPwd 123456 -signVer v2 \${signSwitch}"
                                echo "Executing command: \$cmd1"

                                # 执行 FairGuard 命令
                                eval \$cmd1 || {
                                    echo "Error code: \$?" > "\$error_file"
                                    # 如果 FairGuard 命令失败，恢复原始文件名
                                    mv "\$newFilePath" "\$originalReinforceFilePath"
                                    return 1
                                }

                                # FairGuard 命令成功，恢复原始文件名
                                mv "\$tempReinforceFilePath" "\$originalReinforceFilePath"
                            }

                            # 安卓普通打包
                            # 创建临时文件
                            error_file=\$(mktemp)
                            # 搜索目录
                            num=0
                            maxjobs=3

                            # 拷贝任务FairGuard
                            fairguardToolLocalPath="${AndroidFairguardLocalToolPath}/${BUILD_ID}"
                            if [ -d "\${fairguardToolLocalPath}" ]; then
                                echo "\${fairguardToolLocalPath} Directory exists."
                            else
                                echo "\${fairguardToolLocalPath} Directory does not exist."
                                mkdir -p \${fairguardToolLocalPath}
                                cp -rf ${AndroidFairguardFTPToolPath}/* \${fairguardToolLocalPath}
                            fi

                            for file in \$(find ${PublishReinforceTempPath} -type f \\( -iname \\*.apk -o -iname \\*.aab \\));
                            do
                                echo \$((++num))
                                runFairGuard \$file \$num &
                                # limit jobs
                                if (( \$((\$num % \$maxjobs)) == 0 )) ; then
                                    wait # wait until all have finished (not optimal, but most times good enough)
                                    echo \$num wait
                                fi
                            done
                            wait

                            # 检查是否有错误发生
                            if [ -s "\$error_file" ]; then
                                read error_status < "\$error_file"
                                echo "加固失败了: \$error_status"
                                rm "\$error_file"
                                exit \$error_status
                            fi

                            #结束后移除Fairguard文件
                            if [[ "\$fairguardToolLocalPath" == *"FairGuard"* ]]; then
                                rm -rf \${fairguardToolLocalPath}
                            fi

                            #对签名映射关系做拆分
                            getValueFromStr() {
                                local str="\$1"
                                local filename="\$2"

                                IFS=',' read -ra pairs <<< "\$str"
                                declare -A map=()

                                for pair in "\${pairs[@]}"; do
                                    IFS='=' read -r key value <<< "\$pair"
                                    map["\$key"]="\$value"
                                done

                                for key in "\${!map[@]}"; do
                                    if [[ "\$filename" == *"\${key}"* ]]; then
                                    echo "\${map[\$key]}"
                                    return 0  # 返回 0 表示找到了匹配项
                                    fi
                                done

                                echo "false"
                                return 0  # 返回非0 Jenkins会终止 所以还是返回0
                            }

                            #签名+拷贝
                            signAndMove() {
                                # 第一个参数为 bat文件
                                bat_file=\$1
                                # 第二个参数为 apkfile
                                file=\$2
                                # 第三个参数为 导出的apk名称
                                tofile=\$3
                                # 执行重签命令
                                '//192.168.0.220/出包目录/AndroidPackageTool/sign/'\$bat_file \$file \$tofile
                                # 移动重签后的文件
                                mv "\$tofile" "${PublishPathFinal}"
                            }

                            #删除加固临时目录
                            rm -rf ${PublishReinforceTempPath}/temp

                            for file in \$(find ${PublishReinforceTempPath} -type f \\( -iname \\*encrypted.apk -o -iname \\*encrypted.aab -o -iname \\*fairguard_protected.apk -o -iname \\*fairguard_protected.aab \\)); do

                                fullname=\$(basename \$file)
                                dir=\$(dirname \$file)
                                filename="\${fullname%.*}"
                                echo "\$fullname" | grep -q -E '\\.aab\$' && extension="aab" || extension="apk"
                                echo \$dir , \$fullname , \$filename , \$extension
                                value=\$(getValueFromStr "\$signConfigs" "\$filename")
                                if [[ "\$value" != "false" ]]; then
                                    #如果有配置签名关系 就使用对应的签名
                                    echo "'\$filename' call resign_'\$value'."
                                    signAndMove reSign_\${value}.bat \$file \$dir/\$filename.resign.\${extension};
                                elif [ \$PublishChannelType != 'Intranational' ]; then
                                    #如果没有配置对应的签名关系且属于海外包 默认使用通用签名
                                    echo "'\$filename' call common sign"
                                    signAndMove "reSign.bat" \$file \$dir/\$filename.resign.\${extension};
                                else
                                    #其余情况无需重签名 直接拷贝
                                    echo "'\$filename' no need sign"
                                    mv \$file ${PublishPathFinal}
                                fi

                            done

                            """
                        } else {
                            echo '不走安卓加固逻辑'
                        }
                    }
                }
            }
        }
        stage('ios编译') {
            when {
                // params.BuildWithPlatform && params.PublishPlatform == 'IOS'
                // 不添加 params.BuildWithPlatform 的原因: 该部分需要判断是否 BuildWithPlatform 用于拷贝工程到渲染机器
                expression { params.PublishPlatform == 'IOS' }
            }
            steps {
                dir("${GameProjectPath}/IOSProject") {
                    script {
                        println '参数初始化开始'
                        env.XCODE_PATH = '/Applications/Xcode.app/Contents/Developer/usr/bin/xcodebuild'
                        env.unitywork = "${GameProjectPath}/IOSProject/Unity-iPhone.xcworkspace"
                        env.mytime = new Date().format('yyyyMMdd_HH.mm.ss')
                        env.IPA_PATH = "${GameProjectPath}/iOSBuild/Jenkins/pack/"
                        env.IPA_NAME_PREFIX = "${ProjectEnglishName}_${IOSIpaChannelName}_${IOSConfigpath}_${PublishVersionName}_${mytime}_${ProductParm4Name}"
                        env.m_log_folder_path = "/Users/admin/Jenkins/${ProjectEnglishName}/log"
                        env.m_log_path = "${m_log_folder_path}/${mytime}.txt"

                        if (! fileExists("${m_log_folder_path}")) {
                            sh(returnStdout: false, script: 'mkdir -p ${m_log_folder_path}')
                        }
                        env.PATH_ENCRYPTED_ARCHIVE = "${GameProjectPath}/IOSProject/build/Unity-iPhone-encrypted.xcarchive"
                        //这里应该共用一个变量,加固与否改里面内容就是,这样流程可以统一
                        env.PATH_ARCHIVE = "${GameProjectPath}/IOSProject/Unity-iPhone.xcarchive"
                        env.CMD_EXPORT_ARCHIVE = "${XCODE_PATH} archive -destination 'generic/platform=iOS' -workspace ${unitywork} -scheme Unity-iPhone -archivePath ${PATH_ARCHIVE}"
                        env.CMD_EXPORT_IPA = "${XCODE_PATH} -exportArchive -archivePath replace_iosArchivePath -exportPath ${IPA_PATH} -exportOptionsPlist ${GameProjectPath}/IOSResource/${PublishChannel}/replace_config.plist >> ${m_log_path}"

                        println '参数初始化结束'

                        replaceGameConfigTo("${GameProjectPath}/IOSProject/Data/Raw")

                        sh(returnStdout: false, script: 'security unlock-keychain -p "wckj@mac" ~/Library/Keychains/login.keychain')

                        sh(returnStdout: false, script: 'rm -rf ${GameProjectPath}/IOSProject/build/')
                        sh(returnStdout: false, script: '${XCODE_PATH} -target Unity-iPhone clean')

                        if (! fileExists("${GameProjectPath}/IOSProject/Podfile")) {
                            echo '拷贝文件 Podfile'
                            sh "cp -f ${GameProjectPath}/IOSResource/${PublishChannel}/Podfile ${GameProjectPath}/IOSProject/Podfile"
                        }
                        echo '拷贝文件 Podfile 结束'


                        if (sh(returnStatus: true, script: "grep -q 'CODE_SIGNING_ALLOWED' '${GameProjectPath}/IOSProject/Podfile'") != 0) {
                            echo '补充 cocoapods 对 SDK库签名的处理'
                            sh(returnStdout: false, script: 'cat /Volumes/出包目录/ios_podfile/podfile_cat >> ${GameProjectPath}/IOSProject/Podfile')
                        }
                        echo '补充 cocoapods 对 SDK库签名的处理 结束'

                        sh(returnStdout: false, script: '/usr/local/bin/pod install --repo-update')

                        if (params.BuildWithPlatform) {
                            echo '执行打包逻辑'

                            sh(returnStdout: false, script: 'rm -rf ${IPA_PATH}')

                            sh(returnStdout: false, script: 'mkdir -p /Users/admin/Jenkins/$ProjectEnglishName/log')

                            env.isHasBuildArchive = false
                            def iosBuildTypes = []
                            if (PublishProductType == 'both') {
                                iosBuildTypes.add('release')
                                iosBuildTypes.add('debug')
                            } else {
                                iosBuildTypes.add(PublishProductType)
                            }
                            for (iosBuildType in iosBuildTypes) {
                                println("执行逻辑，当前类型为: $iosBuildType")
                                if (params.PublishReinforce) {
                                    iOSBuildFunWithFairGuard(iosBuildType)
                                } else {
                                    iOSBuild(iosBuildType)
                                }

                                println("IPA Path: ${PublishPathFinal}/${IPA_NAME}.ipa")
                                println("GameProject Path: ${GameProjectPath}")
                            }
                        } else {
                            echo '不执行打包逻辑，拷贝到渲染机器'
                            sh """
                                cd $GameProjectPath
                                scp -r $GameProjectPath/IOSProject xuanran@192.168.3.237:~/xcodeProject/${ProjectEnglishName}-${PublishPlatform}-Jenkins${BUILD_ID}
                            """
                        }
                    }
                }
            }
        }
        stage('鸿蒙编译') {
            when {
                expression { params.BuildWithPlatform && params.PublishPlatform == 'OpenHarmony' }
            }
            steps {
                dir("$GameProjectPath/NewSoccerPublishOpenHarmonyProject") {
                    script {
                        def server = getValueFromStr(AndroidPublishServerStringMap, PublishServer)
                        env.server = server
                        sh """
                        echo "env.PublishPathFinal-->${env.PublishPathFinal}"
                        mkdir -p ${env.PublishPathFinal}
                        current_timestamp=\$(date +%Y%m%d_%H%M%S)
                        if [ "$PublishProductType" = "hap" ]; then
                            #拷贝包
                            #cp \${bash_gameProjectPath}/NewSoccerPublishOpenHarmonyProject ${env.PublishPathFinal}/NewSoccer_Harmony_${PublishVersionCode}_\${current_timestamp}.hap
                            ./hvigorw.bat clean assembleHap
                            cp ./entry/build/default/outputs/default/*-signed.hap ${env.PublishPathFinal}/ChampionsClub_HarmonyOS_Huawei_${PublishVersionCode}_\${current_timestamp}_${ProductParm4Name}_\${server}.hap
                        else
                            #拷贝工程
                            #cp -r \${bash_gameProjectPath}/NewSoccerPublishOpenHarmonyProject ${env.PublishPathFinal}/NewSoccer_Harmony_${PublishVersionCode}_\${current_timestamp}
                            ./hvigorw.bat clean assembleApp
                            cp ./build/outputs/default/*-signed.app ${env.PublishPathFinal}/ChampionsClub_HarmonyOS_Huawei_${PublishVersionCode}_\${current_timestamp}_${ProductParm4Name}_\${server}.app
                        fi
                        """
                    }
                }
            }
        }

        stage('mac编译') {
            when {
                expression { params.BuildWithPlatform && params.PublishPlatform == 'MAC' }
            }
            steps {
                dir("${GameProjectPath}/MacProject/${GameDirName}") {
                    script {
                        // 尝试不解锁钥匙串
                        // sh(returnStdout: false, script: 'security unlock-keychain -p "wckj@mac" ~/Library/Keychains/login.keychain')

                        println '参数初始化开始'
                        env.XCODE_PATH = '/Applications/Xcode.app/Contents/Developer/usr/bin/xcodebuild'
                        env.unitywork = "-workspace ${GameProjectPath}/MacProject/${GameDirName}/MacProject.xcworkspace"
                        env.mytime = new Date().format('yyyyMMdd_HH.mm.ss')
                        env.IPA_PATH = "${GameProjectPath}/MACBuild/Jenkins/pack/"

                        env.m_log_folder_path = "/Users/admin/Jenkins/${ProjectEnglishName}/log"
                        env.m_log_path = "${m_log_folder_path}/${mytime}.txt"

                        if (! fileExists("${m_log_folder_path}")) {
                            sh(returnStdout: false, script: 'mkdir -p ${m_log_folder_path}')
                        }

                        env.IPA_NAME_PREFIX = "${ProjectEnglishName}_${MACChannelName}_${MACConfigpath}_${mytime}_${ProductParm4Name}"

                        //这里应该共用一个变量,加固与否改里面内容就是,这样流程可以统一
                        env.PATH_ARCHIVE = "$GameProjectPath/MacProject/${GameDirName}/MacProject.xcarchive"
                        env.CMD_EXPORT_ARCHIVE = "${XCODE_PATH} archive -destination 'generic/platform=macOS' ${unitywork} -scheme OperableBasketball -archivePath ${PATH_ARCHIVE}"
                        env.CMD_EXPORT_IPA = "${XCODE_PATH} -exportArchive -archivePath replace_macArchivePath -exportPath ${IPA_PATH} -exportOptionsPlist ${GameProjectPath}/MACResource/${PublishChannel}/replace_config.plist >> ${m_log_path}"

                        sh(returnStdout: false, script: 'rm -rf ${IPA_PATH}')

                        def macBuildTypes = []
                        if (PublishProductType == 'both') {
                            macBuildTypes.add('release')
                            macBuildTypes.add('debug')
                        } else {
                            macBuildTypes.add(PublishProductType)
                        }

                        for (macBuildType in macBuildTypes) {
                            println("执行逻辑，当前类型为: $macBuildType")
                            env.IPA_NAME = "${IPA_NAME_PREFIX}_${macBuildType}"
                            sh(returnStdout: false, script: 'eval $CMD_EXPORT_ARCHIVE')
                            env.NEW_CMD_EXPORT_IPA = CMD_EXPORT_IPA.replace('replace_macArchivePath', PATH_ARCHIVE).replace('replace_config', iosBuildType)
                            println("NEW_CMD_EXPORT_IPA: $NEW_CMD_EXPORT_IPA")
                            sh(returnStdout: false, script: 'eval $NEW_CMD_EXPORT_IPA')
                            sh(returnStdout: false, script: 'mkdir -p $PublishPathFinal')
                            sh(returnStdout: false, script: '[ -d "$IPA_PATH" ] && [ "$(ls -A $IPA_PATH/*.app)" ] && cp -rf $IPA_PATH/*.app $PublishPathFinal/$IPA_NAME.app >>${m_log_path}')
                            sh(returnStdout: false, script: '[ -d "$IPA_PATH" ] && [ "$(ls -A $IPA_PATH/*.pkg)" ] && cp -rf $IPA_PATH/*.pkg $PublishPathFinal/$IPA_NAME.pkg >>${m_log_path}')
                        }
                    }
                }
            }
        }
    }
    post {
        success {
            script {
                if (params.upload_symbols_to_bugly) {
                    build job: 'platform_qd_upload_symbols_to_bugly', parameters: [
                            string(name: 'PublishPlatform', value: env.PublishPlatform),
                            string(name: 'PublishChannelType', value: env.PublishChannelType),
                            string(name: 'PublishChannel', value: env.PublishChannel),
                            string(name: 'PublishSymbolsPath', value: env.PublishSymbolsPath.replaceFirst('^\\\\', '\\\\\\\\')),
                            string(name: 'PublishVersionName', value: env.PublishVersionName),
                            string(name: 'ProjectEnglishName', value: "${ProjectEnglishName}"),
                            string(name: 'job_switch', value: env.upload_symbols_to_bugly)
                    ], wait: false
                    echo '触发了 platform_qd_upload_symbols_to_bugly 项目，并传递了参数。'
                } else {
                    echo 'upload_symbols_to_bugly 参数为 false，不触发其他项目。'
                }
            }
        }
        failure {
            // 当构建失败时执行
            script {
                try {
                    //错误关键词列表
                    def errorKeywords = ['error', 'fail', 'exception']
                    //排除的关键词列表
                    def excludeKeywords = ['skipped due']

                    // 读取当前构建的控制台输出
                    def log = currentBuild.rawBuild.getLog(1000).join('\n')
                    // 搜索包含"error"或"fail"的行
                    def matches = log.readLines().findAll { line ->
                        // 检查当前行是否包含数组中的任何一个错误关键词
                        boolean containsError = errorKeywords.any { keyword ->
                            line.toLowerCase().contains(keyword)
                        }
                        // 同时确保不包含排除关键词
                        boolean containsExclusion = excludeKeywords.any { keyword ->
                            line.toLowerCase().contains(keyword)
                        }
                        // 只有当包含错误关键词且不包含排除关键词时，才满足条件
                        containsError && !containsExclusion
                    }

                    if (matches.isEmpty()) {
                        println('没有找到包含错误关键词的行')
                        return
                    }

                    // 将匹配的行串联成一个字符串
                    def errorSummary = matches.join('\n')

                    // 输出匹配的行
                    echo "可能引发报错的堆栈:\n${errorSummary}"

                    errorSummary = matches.collect { "- $it" }.join(';').replaceAll('\r\n', '').replaceAll('\r', '').replaceAll('\n', '');

                    errorSummary = "${env.BUILD_URL};" + errorSummary

                    //发送钉钉通知
                    sh """

                        get_ding_info() {
                            section=\$1
                            config_file=\$2

                            # 读取 token 和 secret
                            token=\$(awk -F: '/'"\$section"'/ {flag=1; next} /token/ && flag {print \$2; flag=0}' \$config_file) || true
                            secret=\$(awk -F: '/'"\$section"'/ {flag=1; next} /secret/ && flag {print \$2; flag=0}' \$config_file) || true

                            # 去除可能存在的空格以及\r和\n
                            local DingToken=\$(echo \$token | tr -d '\r\n' | xargs) || true
                            local DingSecret=\$(echo \$secret | tr -d '\r\n' | xargs) || true

                            echo "\$DingToken \$DingSecret"
                        }

                        WINDingTokenPath='//192.168.0.220/出包目录/Jenkins缓存/机器人缓存/dingtoken.ini'
                        WINDingJarPath='//192.168.0.220/出包目录/Jenkins缓存/jar/DingPush.jar'
                        SDKWINPhoneFilePath='//192.168.0.220/出包目录/Jenkins缓存/联系人缓存/SDKPlatform/磁盘预警联系人.txt'

                        MACDingJarPath='/Volumes//出包目录/Jenkins缓存/jar/DingPush.jar'
                        SDKMACPhoneFilePath='/Volumes//出包目录/Jenkins缓存/联系人缓存/SDKPlatform/磁盘预警联系人.txt'

                        if [ "$PublishPlatform" = "Android" ]; then
                            DingJarPath=\${WINDingJarPath}
                            PhoneFilePath=\${SDKWINPhoneFilePath}
                        else
                            DingJarPath=\${MACDingJarPath}
                            PhoneFilePath=\${SDKMACPhoneFilePath}
                        fi

                        SDKDingToken='d86058b7c059611c81f114c0903ce8cfbcb05c7c7d14e0236df65f9c84c1dce3'
                        SDKDingSecret='SEC5225e9a53f6448f27b3c3d90024a4158105a0735978ff85e6dcd745049cf021e'

                        send_msg="$errorSummary"
                        send_phone=\$(awk -F',' '{print \$2; exit}' "\${PhoneFilePath}") || true
                        java -jar "\$DingJarPath" "\$SDKDingToken" "\$SDKDingSecret" "\$send_msg" "\$send_phone" || true
                        """
                } catch (Exception e) {
                    echo "捕获到错误: ${e.getMessage()}"
                }
            }
        }
    }
}
