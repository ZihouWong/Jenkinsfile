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

    def sayHello(String name) {
        echo "Hello world ${name}"
    }

    def sayHello2(String name) {
        echo "Hello world 222 ${name}"
    }
}

return Utils.getInstance()