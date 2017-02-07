package u

import java.io.*
import java.nio.charset.StandardCharsets

class FileNotFound(val path: Path) : Exception("'$path'")

interface FileInput {
	// Return null if the file could not be found.
	fun read(path: Path): String?
}

class NativeFileInput(val rootDir: Path) : FileInput {
	override fun read(path: Path): String {
		val fullPath = Path.resolveWithRoot(rootDir, path).toString()
		return try {
			File(fullPath).readText()
		} catch (_: FileNotFoundException) {
			throw FileNotFound(path)
		}
	}
}
