// TARGET_BACKEND: JVM_IR
// !DIAGNOSTICS: -UNUSED_PARAMETER

class <!CONFLICTING_JVM_DECLARATIONS!>C<!> {
    <!CONFLICTING_JVM_DECLARATIONS!>fun `a$default`(c: C, x: Int, m: Int, mh: Any)<!> {}
    fun a(x: Int = 1) {}
}