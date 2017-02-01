import compile.err.CompileError
import org.objectweb.asm.*
import n.*
import u.*
import compile.parse.*

interface CompilerHost {
	val io: FileInput
}

//move
class Compiler(private val host: CompilerHost) {
	private var modules = hashMapOf<Path, Module>()

	fun lex(path: Path): Arr<LexedEntry> =
		usePath(path, ::lexToArray)

	fun parse(path: Path): ast.Class =
		usePath(path) { parseClass(it, path.last) }

	private fun<T> usePath(path: Path, f: (Input) -> T): T =
		doWork(path) {
			FileInput.read(host.io, path, f)
		}

	private fun<T> doWork(fullPath: Path, f: Thunk<T>): T =
		try {
			f()
		} catch (error: CompileError) {
			error.path = fullPath
			outputError(error)
			throw error
		}

	private fun outputError(error: CompileError) {
		val message = error.output(this::translateLoc)
		System.err.println(message)
	}

	private fun translateLoc(fullPath: Path, loc: Loc): LcLoc =
		FileInput.read(host.io, fullPath) { source ->
			LcLoc.from(source, loc)
		}
}

object DumbassIo : FileInput {
	override fun open(path: Path): Input =
		StringInput("FOO")
}

object DumbassHost : CompilerHost {
	override val io = DumbassIo
}

fun main(args: Array<String>) {
	val c = Compiler(DumbassHost)
	val l = c.lex(Path.from("a", "b"))
	println(l)
}
