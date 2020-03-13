/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin

/**
 * Adds the specified exception to the list of exceptions that were
 * suppressed in order to deliver this exception.
 */
@SinceKotlin("1.4")
public actual fun Throwable.addSuppressed(exception: Throwable) {
    if (this !== exception) initSuppressed().add(exception)
}

/**
 * Returns a list of all exceptions that were suppressed in order to deliver this exception.
 */
@SinceKotlin("1.4")
public actual val Throwable.suppressedExceptions: List<Throwable>
    get() {
        return this.asDynamic()._suppressed?.unsafeCast<List<Throwable>>() ?: emptyList()
    }

private fun Throwable.initSuppressed(): MutableList<Throwable> {
    return this.asDynamic()._suppressed?.unsafeCast<MutableList<Throwable>>()
        ?: mutableListOf<Throwable>().also { this.asDynamic()._suppressed = it }
}