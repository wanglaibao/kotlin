/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineNameIdState
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.data.State
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.isSubtype

data class CoroutineWithRestoredStack(val coroutine: CoroutineHolder?, val stackFrameItems: List<CoroutineStackFrameItem>)

data class CoroutineHolder(val value: ObjectReference, val info: CoroutineNameIdState) {
    companion object {
        fun lookup(value: ObjectReference, context: DefaultExecutionContext): CoroutineHolder? {
//            val coroutineContext = value.getValue(value.referenceType().fieldByName("context")) as ObjectReference
            val reference = CoroutineContextReference(context)
            val state = reference.state(value, context) ?: return null
            return CoroutineHolder(value, state)
        }
    }
}


class CoroutineContextReference(context: DefaultExecutionContext) {
    // java.lang.Object
    val classClsRef = context.findClass("java.lang.Object") as ClassType
    val toString: Method = classClsRef.concreteMethodByName("toString", "()Ljava/lang/String;")

    // java.util.List
    private val listClsRef = context.findClass("java.util.List") as InterfaceType
    private val sizeRef: Method = listClsRef.methodsByName("size").single()
    private val getRef: Method = listClsRef.methodsByName("get").single()

    // java.lang.StackTraceElement
    private val stackTraceElementClsRef = context.findClass("java.lang.StackTraceElement") as ClassType
    private val methodNameFieldRef: Field = stackTraceElementClsRef.fieldByName("methodName")
    private val declaringClassFieldRef: Field = stackTraceElementClsRef.fieldByName("declaringClass")
    private val fileNameFieldRef: Field = stackTraceElementClsRef.fieldByName("fileName")
    private val lineNumberFieldRef: Field = stackTraceElementClsRef.fieldByName("lineNumber")

    // java.lang.Class
    val classType = context.findClass("java.lang.Class") as ClassType

    val standaloneCoroutine = StandaloneCoroutine(context)


    fun string(state: ObjectReference, context: DefaultExecutionContext): String =
        (context.invokeMethod(state, toString, emptyList()) as StringReference).value()

    fun elementFromList(instance: ObjectReference, num: Int, context: DefaultExecutionContext) =
        context.invokeMethod(
            instance, getRef,
            listOf(context.vm.virtualMachine.mirrorOf(num))
        ) as ObjectReference

    fun sizeOf(args: ObjectReference, context: DefaultExecutionContext): Int =
        (context.invokeMethod(args, sizeRef, emptyList()) as IntegerValue).value()

    fun stackTraceElement(frame: ObjectReference) =
        StackTraceElement(
            fetchClassName(frame),
            fetchMethodName(frame),
            fetchFileName(frame),
            fetchLine(frame)
        )

    private fun fetchLine(instance: ObjectReference) =
        (instance.getValue(lineNumberFieldRef) as? IntegerValue)?.value() ?: -1

    private fun fetchFileName(instance: ObjectReference) =
        (instance.getValue(fileNameFieldRef) as? StringReference)?.value() ?: ""

    private fun fetchMethodName(instance: ObjectReference) =
        (instance.getValue(methodNameFieldRef) as? StringReference)?.value() ?: ""

    private fun fetchClassName(instance: ObjectReference) =
        (instance.getValue(declaringClassFieldRef) as? StringReference)?.value() ?: ""

    fun state(value: ObjectReference, context: DefaultExecutionContext): CoroutineNameIdState? {
        val standAloneCoroutineMirror = standaloneCoroutine.mirror(value, context)
        val name = standAloneCoroutineMirror?.context?.name ?: "coroutine"
        val id = standAloneCoroutineMirror?.context?.id
        val toString = string(value, context)
        val r = """\w+\{(\w+)\}\@([\w\d]+)""".toRegex()
        val matcher = r.toPattern().matcher(toString)
        if (matcher.matches()) {
            val state = stateOf(matcher.group(1))
            val hexAddress = matcher.group(2)
            return CoroutineNameIdState(name, id?.toString() ?: hexAddress, state)
        }
        return null
    }

    private fun stateOf(state: String?): State =
        when (state) {
            "Active" -> State.RUNNING
            "Cancelling" -> State.SUSPENDED_CANCELLING
            "Completing" -> State.SUSPENDED_COMPLETING
            "Cancelled" -> State.CANCELLED
            "Completed" -> State.COMPLETED
            "New" -> State.NEW
            else -> State.UNKNOWN
        }

}

abstract class BaseMirror<T>(val name: String, context: DefaultExecutionContext) {
    val log by logger
    protected val cls = context.findClass(name) ?: throw IllegalStateException("Can't find class ${name} in remote jvm.")

    fun makeField(fieldName: String): Field =
        cls.fieldByName(fieldName) // childContinuation

    fun makeMethod(methodName: String): Method =
        cls.methodsByName(methodName).single()

    fun isCompatible(value: ObjectReference) =
        value.referenceType().isSubTypeOrSame(name)

    fun mirror(value: ObjectReference, context: DefaultExecutionContext): T? {
        if (!isCompatible(value)) {
            log.warn("Value ${value.referenceType()} is not compatible with $name.")
        }
        return fetchMirror(value, context)
    }

    fun staticObjectValue(fieldName: String): ObjectReference {
        val keyFieldRef = makeField(fieldName)
        return cls.getValue(keyFieldRef) as ObjectReference
    }

    fun stringValue(value: ObjectReference, field: Field) =
        (value.getValue(field) as StringReference).value()

    fun stringValue(value: ObjectReference, method: Method, context: DefaultExecutionContext) =
        (context.invokeMethod(value, method, emptyList()) as StringReference).value()

    fun objectValue(value: ObjectReference, method: Method, context: DefaultExecutionContext, vararg values: Value) =
        context.invokeMethodAsObject(value, method, *values)

    fun longValue(value: ObjectReference, method: Method, context: DefaultExecutionContext, vararg values: Value) =
        (context.invokeMethodAsObject(value, method, *values) as LongValue).longValue()

    fun objectValue(value: ObjectReference, field: Field) =
        value.getValue(field) as ObjectReference

    fun intValue(value: ObjectReference, field: Field) =
        (value.getValue(field) as IntegerValue).intValue()

    fun longValue(value: ObjectReference, field: Field) =
        (value.getValue(field) as LongValue).longValue()

    protected abstract fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): T?
}

class StandaloneCoroutine(context: DefaultExecutionContext) :
    BaseMirror<MirrorOfStandaloneCoroutine>("kotlinx.coroutines.StandaloneCoroutine", context) {
    private val coroutineContextMirror = CoroutineContext(context)
    private val childContinuationMirror = ChildContinuation(context)
    private val stateFieldRef: Field = makeField("_state") // childContinuation
    private val contextFieldRef: Field = makeField("context")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfStandaloneCoroutine {
        val state = objectValue(value, stateFieldRef)
        val childcontinuation = childContinuationMirror.mirror(state, context)
        val cc = objectValue(value, contextFieldRef)
        val coroutineContext = coroutineContextMirror.mirror(cc, context)
        return MirrorOfStandaloneCoroutine(value, childcontinuation, coroutineContext)
    }

}

data class MirrorOfStandaloneCoroutine(val that: ObjectReference, val state: MirrorOfChildContinuation?, val context: MirrorOfCoroutineContext?)


class ChildContinuation(context: DefaultExecutionContext) :
    BaseMirror<MirrorOfChildContinuation>("kotlinx.coroutines.ChildContinuation", context) {
    private val childContinuationMirror = CancellableContinuationImpl(context)
    private val childFieldRef: Field = makeField("child") // cancellableContinuationImpl

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfChildContinuation? {
        val child = objectValue(value, childFieldRef)
        return MirrorOfChildContinuation(value, childContinuationMirror.mirror(child, context))
    }
}

data class MirrorOfChildContinuation(
    val that: ObjectReference,
    val child: MirrorOfCancellableContinuationImpl?
)

// kotlinx.coroutines.CancellableContinuationImpl
class CancellableContinuationImpl(context: DefaultExecutionContext) :
    BaseMirror<MirrorOfCancellableContinuationImpl>("kotlinx.coroutines.CancellableContinuationImpl", context) {
    private val coroutineContextMirror = CoroutineContext(context)
    private val decisionFieldRef: Field = makeField("_decision")
    private val delegateFieldRef: Field = makeField("delegate") // DispatchedContinuation
    private val resumeModeFieldRef: Field = makeField("resumeMode")
    private val submissionTimeFieldRef: Field = makeField("submissionTime")
    private val contextFieldRef: Field = makeField("context")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfCancellableContinuationImpl? {
        val decision = intValue(value, decisionFieldRef)
        val dispatchedContinuation = objectValue(value, delegateFieldRef)
        val submissionTime = longValue(value, submissionTimeFieldRef)
        val resumeMode = intValue(value, resumeModeFieldRef)
        val coroutineContext = objectValue(value, contextFieldRef)
        val contextMirror = coroutineContextMirror.mirror(coroutineContext, context)
        return MirrorOfCancellableContinuationImpl(value, decision, dispatchedContinuation, resumeMode, submissionTime, contextMirror)
    }

}

data class MirrorOfCancellableContinuationImpl(
    val that: ObjectReference,
    val decision: Int,
    val delegate: ObjectReference,
    val resumeMode: Int,
    val submissionTyme: Long,
    val jobContext: MirrorOfCoroutineContext?
)

class CoroutineContext(context: DefaultExecutionContext) :
    BaseMirror<MirrorOfCoroutineContext>("kotlin.coroutines.CoroutineContext", context) {
    val coroutineNameRef = CoroutineName(context)
    val coroutineIdRef = CoroutineId(context)
    val jobRef = Job(context)
    val getContextElement: Method = makeMethod("get")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfCoroutineContext? {
        val coroutineName = getElementValue(value, context, coroutineNameRef)
        val coroutineId = getElementValue(value, context, coroutineIdRef)
        val job = getElementValue(value, context, jobRef)
        return MirrorOfCoroutineContext(value, coroutineName, coroutineId, job)
    }

    fun <T> getElementValue(value: ObjectReference, context: DefaultExecutionContext, keyProvider: ContextKey<T>): T? {
        val elementValue = objectValue(value, getContextElement, context, keyProvider.key()) ?: return null
        return keyProvider.mirror(elementValue, context)
    }
}

data class MirrorOfCoroutineContext(
    val that: ObjectReference,
    val name: String?,
    val id: Long?,
    val job: ObjectReference?
)

abstract class ContextKey<T>(name: String, context: DefaultExecutionContext) : BaseMirror<T>(name, context) {
    abstract fun key() : ObjectReference
}

class CoroutineName(context: DefaultExecutionContext) : ContextKey<String>("kotlinx.coroutines.CoroutineName", context) {
    val key = staticObjectValue("Key")
    val getNameRef: Method = makeMethod("getName")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): String? {
        return stringValue(value, getNameRef, context)
    }

    override fun key() = key
}

class CoroutineId(context: DefaultExecutionContext) : ContextKey<Long>("kotlinx.coroutines.CoroutineId", context) {
    val key = staticObjectValue("Key")
    val getNameRef: Method = makeMethod("getId")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): Long? {
        return longValue(value, getNameRef, context)
    }

    override fun key() = key
}

class Job(context: DefaultExecutionContext) : ContextKey<ObjectReference>("kotlinx.coroutines.Job", context) {
    val key = staticObjectValue("Key")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): ObjectReference? {
        return value
    }

    override fun key() = key


}