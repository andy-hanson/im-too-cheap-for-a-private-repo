package u

import java.io.*
import java.nio.charset.StandardCharsets

interface Input : Closeable {
	companion object {
		val EOF: Char = (-1).toChar()
	}

	// Returns Input.EOF on end of file.
	fun readChar(): Char
}

//move?
class StringInput(val s: String) : Input {
	var idx = 0
	override fun readChar(): Char {
		val ch = if (idx < s.length) s[idx] else Input.EOF
		idx++
		return ch
	}

	override fun close() {}
}


class FileNotFound(val path: Path) : Exception("'$path'")

interface FileInput {
	companion object {
		inline fun<T> read(io: FileInput, path: Path, f: (Input) -> T): T =
			io.open(path).use(f)
	}

	// May throw FileNotFound.
	// User should remember to call input.close()
	fun open(path: Path): Input
}

class NativeFileInput(val rootDir: Path) : FileInput {
	override fun open(path: Path): Input {
		val fullPath = Path.resolveWithRoot(rootDir, path).toString()
		return try {
			NativeInput(FileInputStream(fullPath))
		} catch (_: FileNotFoundException) {
			throw FileNotFound(path)
		}
	}
}

class NativeInput(rawInput: InputStream) : Input {
	val charsInput = BufferedReader(InputStreamReader(rawInput, StandardCharsets.UTF_8))

	override fun readChar() =
		charsInput.read().toChar()

	override fun close() {
		charsInput.close()
	}
}
