package xyz.qweru.cat.util.asm

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import xyz.qweru.cat.util.analysis.FrameStateAnalyzer
import xyz.qweru.cat.util.analysis.StackSizeAnalyzer

private val logger = KotlinLogging.logger {}

@Deprecated("Use the specialized variants")
fun analyseMethod(classNode: ClassNode, methodNode: MethodNode): Array<Frame<BasicValue>?> {
    methodNode.maxStack = 256 // todo: maybe i shouldnt be doing this..
    methodNode.maxLocals = 256

    return Analyzer(BasicInterpreter())
        .analyze(classNode.name, methodNode)
}

fun analyseMethodStackHeight(methodNode: MethodNode): StackSizeAnalyzer =
    StackSizeAnalyzer().also {
        it.analyze(methodNode.instructions)
    }

fun analyseMethodStack(methodNode: MethodNode): FrameStateAnalyzer =
    FrameStateAnalyzer()
        .analyze(methodNode.instructions)