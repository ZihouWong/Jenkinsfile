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

    static def sayHello(String name) {
        return "Hello world ${name}"
    }

    static def sayHello2(String name) {
        return  "Hello world 222 ${name}"
    }
}

return Utils.getInstance()