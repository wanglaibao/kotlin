/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.jdi.GeneratedLocation
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.coroutine.data.ContinuationValueDescriptorImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.data.DefaultCoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.standaloneCoroutineDebuggerEnabled
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.invokeInManagerThread
import org.jetbrains.kotlin.idea.debugger.isSubtype

data class ContinuationHolder(val continuation: ObjectReference, val context: DefaultExecutionContext) {
    val log by logger

    fun getAsyncStackTraceIfAny(): List<CoroutineStackFrameItem> {
        val frames = mutableListOf<CoroutineStackFrameItem>()
        try {
            collectFrames(frames)
        } catch (e: Exception) {
            log.error("Error while looking for variables.", e)
        }
        return frames
    }

    private fun collectFrames(consumer: MutableList<CoroutineStackFrameItem>) {
        var completion = this
        val debugMetadataKtType = debugMetadataKtType() ?: return
        while (completion.isBaseContinuationImpl()) {
            val coroutineStackFrame = context.debugProcess.invokeInManagerThread {
                createLocation(completion, debugMetadataKtType)
            }
            if (coroutineStackFrame != null) {
                consumer.add(coroutineStackFrame)
            }
            completion = completion.findCompletion() ?: break
        }
    }

    private fun createLocation(continuation: ContinuationHolder, debugMetadataKtType: ClassType): DefaultCoroutineStackFrameItem? {
        val instance = invokeGetStackTraceElement(continuation, debugMetadataKtType) ?: return null
        val className = context.invokeMethodAsString(instance, "getClassName") ?: return null
        val methodName = context.invokeMethodAsString(instance, "getMethodName") ?: return null
        val lineNumber = context.invokeMethodAsInt(instance, "getLineNumber")?.takeIf {
            it >= 0
        } ?: return null
        val locationClass = context.findClassSafe(className) ?: return null
        val generatedLocation = GeneratedLocation(context.debugProcess, locationClass, methodName, lineNumber)
        val spilledVariables = getSpilledVariables(continuation, debugMetadataKtType) ?: emptyList()
        return DefaultCoroutineStackFrameItem(generatedLocation, spilledVariables)
    }

    private fun invokeGetStackTraceElement(continuation: ContinuationHolder, debugMetadataKtType: ClassType): ObjectReference? {
        val stackTraceElement =
            context.invokeMethodAsObject(debugMetadataKtType, "getStackTraceElement", continuation.value()) ?: return null

        stackTraceElement.referenceType().takeIf { it.name() == StackTraceElement::class.java.name } ?: return null
        context.keepReference(stackTraceElement)
        return stackTraceElement
    }

    fun getSpilledVariables(): List<XNamedValue>? {
        debugMetadataKtType()?.let {
            return getSpilledVariables(this, it)
        }
        return null
    }

    private fun getSpilledVariables(continuation: ContinuationHolder, debugMetadataKtType: ClassType): List<XNamedValue>? {
        val variables: List<JavaValue> = context.debugProcess.invokeInManagerThread {
            FieldVariable.extractFromContinuation(context, continuation.value(), debugMetadataKtType).map {
                val valueDescriptor = ContinuationValueDescriptorImpl(
                    context.project,
                    continuation,
                    it.fieldName,
                    it.variableName
                )
                JavaValue.create(
                    null,
                    valueDescriptor,
                    context.evaluationContext,
                    context.debugProcess.xdebugProcess!!.nodeManager,
                    false
                )
            }
        } ?: emptyList()
        return variables
    }


    private fun debugMetadataKtType(): ClassType? {
        val debugMetadataKtType = context.debugProcess.invokeInManagerThread { context.findCoroutineMetadataType() }
        if (debugMetadataKtType != null) {
            return debugMetadataKtType
        } else {
            log.warn("Continuation information found but no 'kotlin.coroutines.jvm.internal.DebugMetadataKt' class exists. Please check kotlin-stdlib version.")
        }
        return null
    }

    fun referenceType(): ClassType? =
        continuation.referenceType() as? ClassType

    fun value() =
        continuation

    fun field(field: Field): Value? =
        continuation.getValue(field)

    fun findCompletion(): ContinuationHolder? {
        val type = continuation.type()
        if (type is ClassType && isBaseContinuationImpl(type)) {
            val completionField = type.fieldByName(COMPLETION_FIELD_NAME) ?: return null
            return ContinuationHolder(continuation.getValue(completionField) as? ObjectReference ?: return null, context)
        }
        return null
    }

    fun isBaseContinuationImpl() =
        isBaseContinuationImpl(continuation.type())

    private fun isBaseContinuationImpl(type: Type): Boolean {
        return type is ClassType && type.isSubtype(BASE_CONTINUATION_IMPL_CLASS_NAME)
    }

    companion object {
        val log by logger

        const val BASE_CONTINUATION_IMPL_CLASS_NAME = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
        const val COMPLETION_FIELD_NAME = "completion"

        fun lookup(context: ExecutionContext, method: Method, threadProxy: ThreadReferenceProxyImpl): ContinuationHolder? {
//            if (isInvokeSuspendMethod(method)) {
//                val tmp = context.frameProxy.thisObject() ?: return null
//                if (!isSuspendLambda(tmp.referenceType()))
//                    return null
//                return ContinuationHolder(tmp, context, threadProxy)
//            } else if (isSuspendMethod(method)) {
//                var continuation = getVariableValue(context.frameProxy, CONTINUATION_VARIABLE_NAME) ?: return null
//                context.keepReference(continuation)
//                return ContinuationHolder(continuation, context, threadProxy)
//            } else {
            return null
//            }
        }

        fun lookupForResumeMethodContinuation(
            suspendContext: SuspendContextImpl,
            frame: StackFrameProxyImpl
        ): ContinuationHolder? {
            if (frame.location().isPreExitFrame()) {
                val context = suspendContext.executionContext() ?: return null
                var continuation = frame.variableValue(COMPLETION_FIELD_NAME) ?: return null
                context.keepReference(continuation)
                return ContinuationHolder(continuation, context)
            } else
                return null
        }

        fun coroutineExitFrame(
            frame: StackFrameProxyImpl,
            suspendContext: SuspendContextImpl
        ): XStackFrame? {
            return suspendContext.invokeInManagerThread {
                if (frame.location().isPreFlight()) {
                    if(standaloneCoroutineDebuggerEnabled())
                        log.trace("Entry frame found: ${formatLocation(frame.location())}")
                    constructPreFlightFrame(frame, suspendContext)
                } else
                    null
            }
        }

        fun constructPreFlightFrame(
            invokeSuspendFrame: StackFrameProxyImpl,
            suspendContext: SuspendContextImpl
        ): CoroutinePreflightStackFrame? {
            var frames = invokeSuspendFrame.threadProxy().frames()
            val indexOfCurrentFrame = frames.indexOf(invokeSuspendFrame)
            val indexofgetcoroutineSuspended = findGetCoroutineSuspended(frames)
            // @TODO if found - skip this thread stack
            if (indexOfCurrentFrame > 0 && frames.size > indexOfCurrentFrame && indexofgetcoroutineSuspended < 0) {
                val resumeWithFrame = frames[indexOfCurrentFrame + 1] ?: return null
                val ch = lookupForResumeMethodContinuation(suspendContext, resumeWithFrame) ?: return null

                val coroutineStackTrace = ch.getAsyncStackTraceIfAny()
                return CoroutinePreflightStackFrame.preflight(
                    invokeSuspendFrame,
                    resumeWithFrame,
                    coroutineStackTrace,
                    frames.drop(indexOfCurrentFrame)
                )
            }
            return null
        }

        private fun formatLocation(location: Location): String {
            return "${location.method().name()}:${location.lineNumber()}, ${location.method().declaringType()} in ${location.sourceName()}"
        }


        /**
         * Find continuation for the [frame]
         * Gets current CoroutineInfo.lastObservedFrame and finds next frames in it until null or needed stackTraceElement is found
         * @return null if matching continuation is not found or is not BaseContinuationImpl
         */
        fun lookup(
            context: SuspendContextImpl,
            initialContinuation: ObjectReference?,
//            frame: StackTraceElement,
//            threadProxy: ThreadReferenceProxyImpl
        ): ContinuationHolder? {
            var continuation = initialContinuation ?: return null
//            val classLine = ClassNameLineNumber(frame.className, frame.lineNumber)
            val executionContext = context.executionContext() ?: return null

            do {
//                val position = getClassAndLineNumber(executionContext, continuation)
                // while continuation is BaseContinuationImpl and it's frame equals to the current
                continuation = getNextFrame(executionContext, continuation) ?: return null
            } while (continuation.type().isBaseContinuationImpl()  /* && position != classLine */)

            return if (continuation.type().isBaseContinuationImpl())
                ContinuationHolder(continuation, executionContext)
            else
                return null
        }

        data class ClassNameLineNumber(val className: String?, val lineNumber: Int?)

        private fun getClassAndLineNumber(context: ExecutionContext, continuation: ObjectReference): ClassNameLineNumber {
            val objectReference = findStackTraceElement(context, continuation) ?: return ClassNameLineNumber(null, null)
            val classStackTraceElement = context.findClass("java.lang.StackTraceElement") as ClassType
            val getClassName = classStackTraceElement.concreteMethodByName("getClassName", "()Ljava/lang/String;")
            val getLineNumber = classStackTraceElement.concreteMethodByName("getLineNumber", "()I")
            val className = (context.invokeMethod(objectReference, getClassName, emptyList()) as StringReference).value()
            val lineNumber = (context.invokeMethod(objectReference, getLineNumber, emptyList()) as IntegerValue).value()
            return ClassNameLineNumber(className, lineNumber)
        }

        private fun findStackTraceElement(context: ExecutionContext, continuation: ObjectReference): ObjectReference? {
            val classType = continuation.type() as ClassType
            val methodGetStackTraceElement = classType.concreteMethodByName("getStackTraceElement", "()Ljava/lang/StackTraceElement;")
            return context.invokeMethod(continuation, methodGetStackTraceElement, emptyList()) as? ObjectReference
        }
    }
}

