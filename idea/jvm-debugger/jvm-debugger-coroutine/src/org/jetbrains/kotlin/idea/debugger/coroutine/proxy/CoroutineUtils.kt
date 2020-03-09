/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.*
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.ContinuationHolder.Companion.BASE_CONTINUATION_IMPL_CLASS_NAME
import org.jetbrains.kotlin.idea.debugger.evaluate.DefaultExecutionContext

fun Method.isInvokeSuspend(): Boolean =
    name() == "invokeSuspend" && signature() == "(Ljava/lang/Object;)Ljava/lang/Object;"

fun Method.isContinuation() =
    isInvokeSuspend() && declaringType().isContinuation() /* Perhaps need to check for "Lkotlin/coroutines/Continuation;)" in signature() ? */

fun Method.isSuspendLambda() =
    isInvokeSuspend() && declaringType().isSuspendLambda()

fun Method.isResumeWith() =
    name() == "resumeWith" && signature() == "(Ljava/lang/Object;)V" && (declaringType().isSuspendLambda() || declaringType().isContinuation())

fun Location.isPreFlight(): Boolean {
    val method = safeMethod() ?: return false
    return method.isSuspendLambda() || method.isContinuation()
}

fun ReferenceType.isContinuation() =
    isBaseContinuationImpl() || isSubtype("kotlin.coroutines.Continuation")

fun Type.isBaseContinuationImpl() =
    isSubtype("kotlin.coroutines.jvm.internal.BaseContinuationImpl")

fun ReferenceType.isSuspendLambda() =
    SUSPEND_LAMBDA_CLASSES.any { isSubtype(it) }

fun Location.isPreExitFrame() =
    safeMethod()?.isResumeWith() ?: false

fun StackFrameProxyImpl.variableValue(variableName: String): ObjectReference? {
    val continuationVariable = safeVisibleVariableByName(variableName) ?: return null
    return getValue(continuationVariable) as? ObjectReference ?: return null
}

fun Method.isGetCOROUTINE_SUSPENDED() =
    signature() == "()Ljava/lang/Object;" && name() == "getCOROUTINE_SUSPENDED" && declaringType().name() == "kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt"

fun DefaultExecutionContext.findCoroutineMetadataType() =
    findClassSafe("kotlin.coroutines.jvm.internal.DebugMetadataKt")

fun DefaultExecutionContext.findDispatchedContinuationReferenceType() =
    vm.classesByName("kotlinx.coroutines.DispatchedContinuation")

fun findGetCoroutineSuspended(frames: List<StackFrameProxyImpl>) =
    frames.indexOfFirst { it.safeLocation()?.safeMethod()?.isGetCOROUTINE_SUSPENDED() == true }

/**
 * Finds previous Continuation for this Continuation (completion field in BaseContinuationImpl)
 * @return null if given ObjectReference is not a BaseContinuationImpl instance or completion is null
 */
fun getNextFrame(context: DefaultExecutionContext, continuation: ObjectReference): ObjectReference? {
    if (!continuation.type().isBaseContinuationImpl())
        return null
    val type = continuation.type() as ClassType
    val next = type.concreteMethodByName("getCompletion", "()Lkotlin/coroutines/Continuation;")
    return context.invokeMethod(continuation, next, emptyList()) as? ObjectReference
}

fun SuspendContextImpl.executionContext() =
    invokeInManagerThread { DefaultExecutionContext(EvaluationContextImpl(this, this.frameProxy)) }

fun <T> SuspendContextImpl.invokeInManagerThread(f: () -> T?) : T? =
    debugProcess.invokeInManagerThread { f() }

fun ThreadReferenceProxyImpl.supportsEvaluation(): Boolean =
    threadReference?.isSuspended ?: false

fun SuspendContextImpl.supportsEvaluation() =
    this.debugProcess.canRunEvaluation

fun XDebugSession.suspendContextImpl() =
    suspendContext as SuspendContextImpl

