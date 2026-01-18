package xyz.qweru.cat.util.asm

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.Value

fun analyseMethod(classNode: ClassNode, methodNode: MethodNode): Array<Frame<BasicValue>?> {
    methodNode.maxStack = 65535
    methodNode.maxLocals = 65535

    return Analyzer(BasicInterpreter())
        .analyze(classNode.name, methodNode)
}

fun Frame<BasicValue>.isSameFrame(other: Frame<BasicValue>): Boolean {
    if (stackSize != other.stackSize || locals != other.locals) {
        return false
    }

    for (i in 0..<stackSize) {
        if(getStack(i).type.descriptor != other.getStack(i).type.descriptor) {
            return false
        }
    }

    for (i in 0..<locals) {
        if(getLocal(i).type.descriptor != other.getLocal(i).type.descriptor) {
            return false
        }
    }
}