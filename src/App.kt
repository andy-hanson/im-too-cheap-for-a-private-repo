import compile.err.CompileError
import org.objectweb.asm.*
import n.*
import u.*
import compile.*

object MockIo : FileInput {
	private var map = HashMap<Path, String>()

	override fun read(path: Path) =
		map[path] ?: throw FileNotFound(path)

	fun set(path: Path, content: String) {
		map.set(path, content)
	}
}

object MockHost : CompilerHost {
	override val io = MockIo
}

fun main(args: Array<String>) {
	val h = MockHost
	h.io.set(Path.from("a", "b.nz"), """
slots
	val Int a

fun Int x(Int y)
	y
""")
	val c = Compiler(h)
	//val l = c.lex(Path.from("a", "b"))
	//println(l)

	val ast = c.parse(Path.from("a", "b.nz"))
	println(ast.toSexpr())


	c.compile(Path.from("a", "b"))

}
