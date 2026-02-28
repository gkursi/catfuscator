package xyz.qweru.cat.transform.flow

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.MethodTransformer
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.isStatic
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom

class ControlFlowTransformer(
    target: JarContainer, opts: Configuration
) : Transformer("ControlFlow", "Generic control flow obfuscation", target, opts) {

//    val heavy by value("Heavy", "Heavy flow obfuscation (unstable)", false)
    val shuffle by value("Block Shuffle", "Shuffles basic blocks (unstable-ish)", true)
    val globalVT by value("Global Variable Table", "Make every local exist everywhere", true)

    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) {
                    continue
                }

                val klass = entry.value
                for (method in klass.methods) {
                    parallel {
                        transformMethod(method) {
//                            // Fixme
//                            if (heavy) {
//                                createBlocks(klass, method)
//                            }

                            val blocks = collectBlocks()
                            instructions.clear()

                            val first = blocks[0].labelNode
                            if (shuffle) {
                                blocks.shuffle()
                            }

                            instructions.add(instructionsFor(method) {
                                jump(first)
                                for ((index, block) in blocks.withIndex()) {
                                    +block.labelNode
                                    block.insns.forEach(::instruction)
                                }
                            })

                            if (globalVT) {
                                globalize(method)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun MethodTransformer.globalize(method: MethodNode) {
        val pass = createPass()
        var startLabel: LabelNode? = null
        var endLabel: LabelNode? = null

        pass.find({ it is LabelNode }) { label, _, _ ->
            label as LabelNode
            startLabel = startLabel ?: label
            endLabel = label
        }

        var index = if (method.isStatic) 0 else 1
        val locals = method.localVariables
            .sortedWith(
                Comparator.comparingInt(LocalVariableNode::index)
            )

        for (lv in locals) {
            if (!method.isStatic && lv.index == 0) {
                continue
            }

            val from = instructions.indexOf(lv.start)

            var foundEnd = false
            val to = instructions.indexOfFirst { node ->
                if (node !is LabelNode) {
                    return@indexOfFirst false
                }

                if (foundEnd) {
                    return@indexOfFirst true
                } else if (node == lv.end) {
                    foundEnd = true
                }

                return@indexOfFirst false
            }

            pass.find({ it is VarInsnNode }) { lvn, _, i ->
                lvn as VarInsnNode
                if (i !in from..to || lvn.`var` != lv.index) {
                    return@find
                }

                lvn.`var` = index
            }.find({ it is IincInsnNode }) { lvn, _, i ->
                lvn as IincInsnNode
                if (i !in from..to || lvn.`var` != lv.index) {
                    return@find
                }

                lvn.`var` = index
            }

            lv.start = startLabel
            lv.end = endLabel
            index += if (lv.desc == "J" || lv.desc == "D") 2 else 1
        }
    }

    // fixme
//    private fun MethodTransformer.createBlocks(klass: ClassNode, method: MethodNode) {
//        println("method ${method.name}")
//        val frames = analyseMethod(klass, method)
//        createPass().insertBeforeIndexed({ _, i ->
//            frames[i]?.stackSize == 0
//        }) { _, _, _ ->
//            InsnList().apply { add(LabelNode(Label())) }
//        }
//    }

    private fun MethodTransformer.collectBlocks(): ArrayList<Block> {
        val blocks = arrayListOf<Block>()
        var block: Block? = null
        for (node in instructions) {
            when (node) {
                is LabelNode -> {
                    if (block != null) {
                        block.insns.add(JumpInsnNode(
                            Opcodes.GOTO,
                            node
                        ))
                        blocks.add(block)
                    }

                    block = Block(node, arrayListOf())
                }

                else -> block!!.insns.add(node)
            }
        }

        block?.let { blocks.add(it) }
        return blocks
    }

    private data class Block(val labelNode: LabelNode, val insns: ArrayList<AbstractInsnNode>)
}