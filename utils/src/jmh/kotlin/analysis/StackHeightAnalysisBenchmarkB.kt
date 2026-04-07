package analysis

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import xyz.qweru.cat.util.analysis.FastFrameStateAnalyzer
import xyz.qweru.cat.util.analysis.FastStackSizeAnalyzer
import xyz.qweru.cat.util.asm.ClassBuilder
import xyz.qweru.cat.util.asm.PUBLIC_STATIC
import xyz.qweru.cat.util.asm.analyseMethod
import java.util.concurrent.TimeUnit

@Measurement(timeUnit = TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class StackHeightAnalysisBenchmarkB {
    lateinit var klass: ClassNode
    lateinit var method: MethodNode

    @Setup
    fun setup() {
        klass = ClassNode()

        ClassBuilder(klass).method("", PUBLIC_STATIC, "()V",) {
            val label = label()
            val labelB = label()

            constant1() // s=1

            constant1(); constant2() // s=3
            jumpIfIntGreater(label) // s=1

            +labelB // s=1 on both paths
            constant1() // s=2
            xorInts() // s=1
            dup() // s=2
            jumpIfLessThan(labelB)

            +label // s=1 on both paths
            pop() // s=0
            returnVoid()
        }

        method = klass.methods[0]
    }

    @Benchmark
    fun baselineBench(bh: Blackhole) {
        bh.consume(
            analyseMethod(
                klass,
                method
            )
        )
    }

    @Benchmark
    fun fastBench(bh: Blackhole) {
        bh.consume(
            FastFrameStateAnalyzer()
                .analyze(method.instructions)
        )
    }

}