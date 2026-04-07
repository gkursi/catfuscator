package xyz.qweru.cat.util.asm

import org.objectweb.asm.tree.MethodNode
import xyz.qweru.cat.util.ASM

class CMethodNode : MethodNode(ASM) {
    val duplicates = arrayListOf<MethodNode>()
}