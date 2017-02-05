package compile

import compile.err.*
import compile.parse.*
import compile.check.*
import compile.emit.*
import n.*
import u.*

interface CompilerHost {
	val io: FileInput
}

//move
class Compiler(private val host: CompilerHost) {
	private var modules = HashMap<Path, Module>()
	private var classLoader = DynamicClassLoader()

	fun lex(path: Path): Arr<LexedEntry> {
		val source = host.io.read(path)
		return doWork(Module(path, path, source)) {
			lexToArray(source)
		}
	}

	fun parse(path: Path): ast.Module {
		val source = host.io.read(path)
		return doWork(Module(path, path, source)) {
			parseModule(source, path.last)
		}
	}

	fun compile(path: Path): Module {
		//TODO: linearize *while* we work!!!
		val linearized = linearizeModuleDependencies(host.io, path)
		return compileSingleModule(linearized)
	}

	private fun compileSingleModule(linear: LinearizedModule): Module =
		doWork(linear.module) {
			val (module, ast, imported) = linear
			val imports = imported.map(this::compileSingleModule)
			module.imports = imports

			val klass = makeClass(imports, ast.klass)
			val bytes = classToBytecode(klass, module.lineColumnGetter)
			klass.jClassBytes = bytes
			klass.jClass = classLoader.define(klass.javaTypeName, bytes)

			module.klass = klass
			module
		}

	private fun<T> doWork(module: Module, f: () -> T): T =
		try {
			f()
		} catch (error: CompileError) {
			error.module = module
			throw error
		}


	//private fun translateLoc(fullPath: Path, loc: Loc): LcLoc =
	//	//TODO: Don't do I/O again, cache it. But can't keep it in the module since that won't exist untio after we've parsed...
	//	//But if there's a parse error, we know what file we came from...
	//	//For a checker error we should know the module of the file...
	//	LcLoc.from(host.io.read(fullPath), loc)
}

class DynamicClassLoader : ClassLoader {
	constructor() : super()
	constructor(parent: ClassLoader) : super(parent)

	fun define(className: String, bytecode: ByteArray): Class<out Any> =
		super.defineClass(className, bytecode, 0, bytecode.size)
}
