/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin

// NOTE: Do not author your exceptions as they are written in this file, instead use this template:
/*
public open class MyException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
*/


// TODO: remove primary constructors, make all secondary KT-22055

@Suppress("USELESS_ELVIS_RIGHT_IS_NULL")
public actual open class Error actual constructor(message: String?, cause: Throwable?) : Throwable(message, cause ?: null) {
    actual constructor() : this(null, null)
    actual constructor(message: String?) : this(message, null)
    actual constructor(cause: Throwable?) : this(undefined, cause)
}

@Suppress("USELESS_ELVIS_RIGHT_IS_NULL")
public actual open class Exception actual constructor(message: String?, cause: Throwable?) : Throwable(message, cause ?: null) {
    actual constructor() : this(null, null)
    actual constructor(message: String?) : this(message, null)
    actual constructor(cause: Throwable?) : this(undefined, cause)
}

public actual open class RuntimeException actual constructor(message: String?, cause: Throwable?) : Exception(message, cause) {
    actual constructor() : this(null, null)
    actual constructor(message: String?) : this(message, null)
    actual constructor(cause: Throwable?) : this(undefined, cause)
}

public actual open class IllegalArgumentException actual constructor(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    actual constructor() : this(null, null)
    actual constructor(message: String?) : this(message, null)
    actual constructor(cause: Throwable?) : this(undefined, cause)
}

public actual open class IllegalStateException actual constructor(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    actual constructor() : this(null, null)
    actual constructor(message: String?) : this(message, null)
    actual constructor(cause: Throwable?) : this(undefined, cause)
}

public actual open class IndexOutOfBoundsException actual constructor(message: String?) : RuntimeException(message) {
    actual constructor() : this(null)
}

public actual open class ConcurrentModificationException actual constructor(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    actual constructor() : this(null, null)
    actual constructor(message: String?) : this(message, null)
    actual constructor(cause: Throwable?) : this(undefined, cause)
}

public actual open class UnsupportedOperationException actual constructor(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    actual constructor() : this(null, null)
    actual constructor(message: String?) : this(message, null)
    actual constructor(cause: Throwable?) : this(undefined, cause)
}


public actual open class NumberFormatException actual constructor(message: String?) : IllegalArgumentException(message) {
    actual constructor() : this(null)
}


public actual open class NullPointerException actual constructor(message: String?) : RuntimeException(message) {
    actual constructor() : this(null)
}

public actual open class ClassCastException actual constructor(message: String?) : RuntimeException(message) {
    actual constructor() : this(null)
}

public actual open class AssertionError private constructor(message: String?, cause: Throwable?) : Error(message, cause) {
    actual constructor() : this(null)
    constructor(message: String?) : this(message, null)
    actual constructor(message: Any?) : this(message.toString(), message as? Throwable)
}

public actual open class NoSuchElementException actual constructor(message: String?) : RuntimeException(message) {
    actual constructor() : this(null)
}

@SinceKotlin("1.3")
public actual open class ArithmeticException actual constructor(message: String?) : RuntimeException(message) {
    actual constructor() : this(null)
}

public actual open class NoWhenBranchMatchedException actual constructor(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    actual constructor() : this(null, null)
    actual constructor(message: String?) : this(message, null)
    actual constructor(cause: Throwable?) : this(undefined, cause)
}

public actual open class UninitializedPropertyAccessException actual constructor(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    actual constructor() : this(null, null)
    actual constructor(message: String?) : this(message, null)
    actual constructor(cause: Throwable?) : this(undefined, cause)
}

public actual fun Throwable.addSuppressed(exception: Throwable) {
    if (this !== exception) initSuppressed().add(exception)
}

public actual val Throwable.suppressedExceptions: List<Throwable> get() {
    return this.asDynamic()._suppressed?.unsafeCast<List<Throwable>>() ?: emptyList()
}

private fun Throwable.initSuppressed(): MutableList<Throwable> {
    return this.asDynamic()._suppressed?.unsafeCast<MutableList<Throwable>>()
        ?: mutableListOf<Throwable>().also { this.asDynamic()._suppressed = it }
}