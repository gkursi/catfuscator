package xyz.qweru.cat.util.asm

import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.util.ASM

/**
 * ClassNode with additional metadata
 */
class CClassNode : ClassNode(ASM) {
    val metadata by lazy { arrayListOf<Any>(0, 0, 0, 0) }
}