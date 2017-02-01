package compile.err

import u.*
import compile.parse.Token

sealed class Err {
	// Lexer
	object LeadingSpace : Err() {
		override fun toString() =
			"Line may not begin with a space."
	}

	object NumberMustHaveDigitsAfterDecimalPoint : Err() {
		override fun toString() =
			"A number *must* have digits after the decimal point; e.g. `1.0` and not `1.`."
	}

	object TooMuchIndent : Err() {
		override fun toString() =
			"This line is indented multiple times compared to the previous line. Use only one indent."
	}

	object TrailingSpace : Err() {
		override fun toString() =
			"This line has a trailing space character."
	}

	class UnrecognizedCharacter(val ch: Char) : Err() {
		override fun toString() =
			"Unrecognized character: `$ch`"
	}

	class Unexpected(val t: Token) : Err() {
		override fun toString() =
			"Unexpected token: $t"
	}

	class UnexpectedCharacter(val ch: Char, val expected: String): Err() {
		override fun toString() =
			"Expected '$expected', got '$ch'"
	}

	// Parser


	object EmptyExpression : Err() {
		override fun toString() =
			"This expression is empty."
	}

	object PrecedingEquals : Err() {
		override fun toString() =
			"`=` must be preceded by one or more names."
	}

	object BlockCantEndInDeclare : Err() {
		override fun toString() =
			"Last line of block can't be a variable declaration."
	}

	// Checker
	class CantBind(val name: Sym) : Err() {
		override fun toString() =
			"WTF is $name?"
	}
}

class CompileError(val loc: Loc, val kind: Err) : Exception() {
	constructor(loc: Loc, kind: Err, path: Path) : this(loc, kind) {
		this.path = path
	}

	var path: Path by Late()

	fun output(translateLoc: (Path, Loc) -> LcLoc): String {
		val lcLoc = translateLoc(path, loc)
		return "Error at $path $lcLoc: $kind"
	}
}
