/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.evaluate.DefaultExecutionContext

class CoroutineNoLibraryProxy(val executionContext: DefaultExecutionContext) : CoroutineInfoProvider {
    val log by logger
    val debugMetadataKtType = executionContext.findCoroutineMetadataType()

    override fun dumpCoroutinesInfo(): List<CoroutineInfoData> {
        val vm = executionContext.vm
        val resultList = mutableListOf<CoroutineInfoData>()
        if (vm.virtualMachine.canGetInstanceInfo()) {
            val dcClassTypeList = executionContext.findDispatchedContinuationReferenceType()
            if (dcClassTypeList?.size == 1) {
                val dcClassType = dcClassTypeList.first()
                val continuationField = dcClassType.fieldByName("continuation") ?: return resultList
                val continuationList = dcClassType.instances(maxCoroutines())
                for (dispatchedContinuation in continuationList) {
                    val coroutineInfo = extractDispatchedContinuation(dispatchedContinuation, continuationField) ?: continue
                    resultList.add(coroutineInfo)
                }
            }
        } else
            log.warn("Remote JVM doesn't support canGetInstanceInfo capability (perhaps JDK-8197943).")
        return resultList
    }

    fun extractDispatchedContinuation(dispatchedContinuation: ObjectReference, continuation: Field): CoroutineInfoData? {
        debugMetadataKtType ?: return null
        val initialContinuation = dispatchedContinuation.getValue(continuation) as ObjectReference
        val ch = ContinuationHolder(initialContinuation, executionContext)
        val coroutineWithRestoredStack = ch.getAsyncStackTraceIfAny() ?: return null
        println("!")

        return CoroutineInfoData.suspendedCoroutineInfoData(coroutineWithRestoredStack, initialContinuation)
    }

}

fun maxCoroutines() = Registry.intValue("kotlin.debugger.coroutines.max", 1000).toLong()
