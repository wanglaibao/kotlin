// FILE: test.kt
public val MASSERTIONS_ENABLED: Boolean = true

public inline fun massert(value: Boolean, lazyMessage: () -> String) {
    if (MASSERTIONS_ENABLED) {
        if (!value) {
            val message = lazyMessage()
            throw AssertionError(message)
        }
    }
}


public inline fun massert(value: Boolean, message: Any = "Assertion failed") {
    if (MASSERTIONS_ENABLED) {
        if (!value) {
            throw AssertionError(message)
        }
    }
}

fun box(): String {
    massert(true)
    massert(true) {
        "test"
    }

    return "OK"
}

// LINENUMBERS
// TestKt.box():23
// TestKt.box():14
// TestKt.box():15
// TestKt.getMASSERTIONS_ENABLED():2
// TestKt.box():15
// TestKt.box():16
// TestKt.box():20
// TestKt.box():24
// TestKt.box():5
// TestKt.getMASSERTIONS_ENABLED():2
// TestKt.box():5
// TestKt.box():6
// TestKt.box():11
// TestKt.box():28