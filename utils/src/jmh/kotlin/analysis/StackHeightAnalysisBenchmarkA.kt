package analysis

//@Measurement(timeUnit = TimeUnit.NANOSECONDS)
//@State(Scope.Benchmark)
//open class StackHeightAnalysisBenchmarkA {
//    lateinit var klass: ClassNode
//    lateinit var method: MethodNode
//
//    @Setup
//    fun setup() {
//        klass = ClassNode()
//
//        ClassBuilder(klass).method("", PUBLIC_STATIC, "()V",) {
//            val label = label()
//            constant1() // s=1
//
//            constant1(); constant2() // s=3
//            jumpIfIntGreater(label) // s=1
//
//            constant1() // s=2
//            xorInts() // s=1
//
//            +label // s=1 on both paths
//            pop() // s=0
//            returnVoid()
//        }
//
//        method = klass.methods[0]
//    }
//
//    @Benchmark
//    fun stateBench(bh: Blackhole) {
//        val analyzer = FastFrameStateAnalyzer()
//
//        analyzer.analyze(method.instructions)
//        bh.consume(analyzer.analyze(method.instructions))
//    }
//
//    @Benchmark
//    fun baselineBench(bh: Blackhole) {
//        bh.consume(
//            analyseMethod(
//                klass,
//                method
//            )
//        )
//    }
//
////    @Benchmark
////    fun fastBench(bh: Blackhole) {
////        bh.consume(
////            FastStackSizeAnalyzer()
////                .analyze(method.instructions)
////        )
////    }
//}