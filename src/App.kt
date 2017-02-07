import org.objectweb.asm.*
import org.objectweb.asm.util.TraceClassVisitor
import n.*
import u.*
import compile.*

fun main(args: Array<String>) {
	test()
}

class MockIo() : FileInput {
	private val map = HashMap<Path, String>()

	override fun read(path: Path) =
		map[path]

	fun set(path: Path, content: String) {
		map.set(path, content)
	}
}

class MockHost() : CompilerHost {
	override val io = MockIo()
}

val testSource = """
slots
	val Int a

fun Int x(Int y)
	y + y
"""

fun test() {
	val h = MockHost()
	h.io.set(Path.from("a", "b.nz"), testSource)

	val c = Compiler(h)
	//val l = c.lex(Path.from("a", "b"))
	//println(l)

	val ast = c.parse(Path.from("a", "b.nz"))
	//println(ast.toSexpr())

	val module = c.compile(Path.from("a", "b"))
	//println(module.toSexpr())

	val klass = module.klass
	val j = klass.jClass
	//printClass(klass.jClassBytes)

	val x = j.getMethod("x", Builtins.NzInt::class.java)
	val result = x.invoke(null, Builtins.NzInt(1))
	println(result)
}

fun printClass(bytes: ByteArray) {
	val reader = ClassReader(bytes)
	val visitor = TraceClassVisitor(java.io.PrintWriter(System.out))
	reader.accept(visitor, ClassReader.SKIP_DEBUG)
}



