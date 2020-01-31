// TARGET_BACKEND: JVM_IR
// !DIAGNOSTICS: -UNUSED_PARAMETER

open class Base {
    open fun `foo$default`(d: Derived, i: Int, mask: Int, mh: Any) {}
}

class <!ACCIDENTAL_OVERRIDE!>Derived<!> : Base() {
    fun foo(i: Int = 0) {}
}
