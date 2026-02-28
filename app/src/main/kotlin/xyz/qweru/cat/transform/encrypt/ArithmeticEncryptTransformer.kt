package xyz.qweru.cat.transform.encrypt

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.MethodTransformer
import xyz.qweru.cat.util.asm.TransformPass
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

class ArithmeticEncryptTransformer(
    target: JarContainer,
    opts: Configuration,
) : Transformer("ArithmeticEncrypt", "Encrypt arithmetic ops", target, opts) {
    val simple by value("Simple", "Replace addition/subtraction", true)
    val binary by value("Binary", "Replace binary insns", true)
    val heavyXor by value("Heavy Int XOR", "Enable heavy obfuscation by replacing xor insns", true)

    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) continue
                val klass = entry.value
                for (method in klass.methods) {
                    transformMethod(method) {
                        val pass = createPass()
                        replaceSimple(pass, method)
                        replaceBinary(pass, method)
                        replaceXor(method)
                    }
                }
            }
        }
        parallel.await()
    }

    private fun MethodTransformer.replaceSimple(pass: TransformPass, method: MethodNode) {
        if (!simple) return

        pass.replace({ it.opcode == Opcodes.IADD }) { _, _, _ ->
            instructionsFor(method) {
                when(Random.nextInt(3)) {
                    0 -> {
                        constantM1()
                        xorInts()
                        subInts()
                        constant1()
                        subInts()
                    }
                    1 -> {
                        dup2()
                        orInts()
                        dup_x2()
                        pop()
                        andInts()
                        addInts()
                    }
                    2 -> {
                        dup2()
                        xorInts()
                        dup_x2()
                        pop()
                        andInts()
                        constant1()
                        shlInts()
                        addInts()
                    }
                }
            }
        }

        pass.replace({ it.opcode == Opcodes.ISUB  }) { _, _, _ ->
            instructionsFor(method) {
                when(Random.nextInt(2)) {
                    0 -> {
                        constantM1()
                        xorInts() // a, ~b
                        constant1()
                        addInts()
                        addInts()
                    }
                    1 -> {
                        dup2()
                        xorInts()
                        dup_x2()
                        pop() // a ^ b, a, b
                        swap() // a ^ b, b, a
                        constantM1()
                        xorInts() // a ^ b, b, ~a
                        swap() // a ^ b, ~a, b
                        andInts()
                        constant1()
                        shlInts()
                        subInts()
                    }
                }
            }
        }

        pass.replace({ it.opcode == Opcodes.LADD }) { _, _, _ ->
            instructionsFor(method) {
                ldc(-1L)
                xorLongs()
                subLongs()
                longConstant1()
                subLongs()
            }
        }

        pass.replace({ it.opcode == Opcodes.LSUB  }) { _, _, _ ->
            instructionsFor(method) {
                ldc(-1L)
                xorLongs() // a, ~b
                longConstant1()
                addLongs()
                addLongs()
            }
        }
    }

    private fun MethodTransformer.replaceBinary(pass: TransformPass, method: MethodNode) {
        if (!binary) return

        pass.replace({ it.opcode == Opcodes.IAND }) { _, _, _ ->
            instructionsFor(method) {
                constantM1()
                xorInts()
                swap()
                constantM1()
                xorInts()
                swap() // ~a, ~b
                orInts()
                constantM1()
                xorInts()
            }
        }

        pass.replace({ it.opcode == Opcodes.IOR }) { _, _, _ ->
            instructionsFor(method) {
                constantM1()
                xorInts()
                swap()
                constantM1()
                xorInts()
                swap() // ~a, ~b
                andInts()
                constantM1()
                xorInts()
            }
        }

        pass.replace({ it.opcode == Opcodes.LAND }) { _, _, _ ->
            instructionsFor(method) {
                ldc(-1L)
                xorLongs()
                swap2()
                ldc(-1L)
                xorLongs()
                swap2() // ~a, ~b
                orLongs()
                ldc(-1L)
                xorLongs()
            }
        }

        pass.replace({ it.opcode == Opcodes.LOR }) { _, _, _ ->
            instructionsFor(method) {
                ldc(-1L)
                xorLongs()
                swap2()
                ldc(-1L)
                xorLongs()
                swap2() // ~a, ~b
                andLongs()
                ldc(-1L)
                xorLongs()
            }
        }
    }

    private fun MethodTransformer.replaceXor(method: MethodNode) {
        if (!heavyXor) return

        createPass().replace({ it.opcode == Opcodes.IXOR }) { _, _, _ ->
            instructionsFor(method) {
                when (Random.nextInt(2)) {
                    0 -> {
                        dup2() // a, b, a, b
                        orInts()
                        dup_x2()
                        pop() // a | b, a, b
                        andInts()
                        constantM1()
                        xorInts()
                        andInts()
                    }
                    1 -> {
                        // (~a & b) | (a & ~b)
                        dup2() // a, b, a, b
                        constantM1()
                        xorInts() // a, b, a, ~b
                        andInts()
                        dup_x2()
                        pop() // a & ~b, a, b
                        swap() // a & ~b, b, a
                        constantM1()
                        xorInts() // a & ~b, b, ~a
                        swap()
                        andInts() // a & ~b, ~a & b
                        swap() // ~a & b, a & ~b
                        orInts()
                    }
                }
            }
        }
    }
}