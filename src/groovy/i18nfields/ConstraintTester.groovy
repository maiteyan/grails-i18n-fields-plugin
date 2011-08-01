package i18nfields

/**
 * author: jneira (http://twitter.com/jneira)
 * link: http://meetspock.appspot.com/script/35001
 */
class ConstraintsTester {
    def methodsCalled = [:]

    def invokeMethod(String name, Object args) {
        methodsCalled << [(name): args[0]]
    }

    def test(constraints) {
        this.with constraints
        methodsCalled
    }
}