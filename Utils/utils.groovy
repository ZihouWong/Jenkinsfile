class Utils {

    private static Utils instance

    public String name = "utils name"

    private Utils() {}

    static Utils getInstance() {
        if (instance == null) {
            instance = new Utils()
        }
        return instance
    }

    static String getHomePath(String PublishPlatform) {
        if (PublishPlatform.contains('Android') || PublishPlatform.contains('OpenHarmony')) {
            return 'C:/Users/admin'
        } else {
            return '/Users/admin'
        }
    }

    static String sayHello(String who) {
        return "Hello world, ${who}"
    }
}

return Utils.getInstance()