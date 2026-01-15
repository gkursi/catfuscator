package xyz.qweru.cat.util

import org.objectweb.asm.tree.ParameterNode

data class CatMethodParameter(val node: ParameterNode, val descriptor: String, val signature: String? = null) {
}