package compile.err

import u.*
import compile.parse.Token
import n.*

sealed class Err {
	// Module loader
	class CircularDepdendency(val path: Path) : Err() {
		override fun toString() =
			"There is a circular dependency involving $path"
	}

	class CantFindLocalModuleFromFileOrDirectory(
		val importPath: RelPath,
		val filePath: Path,
		val dirPath: Path)  : Err() {
		override fun toString() =
			"Can't find any module $importPath. Tried $filePath and $dirPath."
	}

	class CantFindLocalModuleFromDirectory(val path: RelPath, val dirPath: Path) : Err()  {
		override fun toString() =
			"Can't find any module %relPath. Tried $dirPath."
	}

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
			"Expected '$expected', got '$ch' (character code ${ch.toInt()})"
	}

	class ExpectedKeyword(val kw: Token.Kw): Err() {
		override fun toString() =
			"Expected ${kw.name}"
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

	class DuplicateMember(val name: Sym) : Err() {
		override fun toString() =
			"Duplicate member name $name"
	}

	class CantBind(val name: Sym) : Err() {
		override fun toString() =
			"WTF is $name?"
	}

	class NameAlreadyBound(val name: Sym) : Err() {
		override fun toString() =
			"$name has already been declared in this scope."
	}

	class CombineTypes(val a: Ty, val b: Ty) : Err() {
		override fun toString() =
			"Can't combine types $a and $b."
	}

	class NoSuchMember(val containingTy: Ty, val memberName: Sym) : Err() {
		override fun toString() =
			"Type $containingTy has no member $memberName"
	}

	class WrongNumberOfArguments(val method: NzMethod, val argsCount: Int) : Err() {
		override fun toString() =
			"Method ${method.name} takes ${method.arity} arguments, got $argsCount"
	}

	class WrongType(val expected: Ty, val actual: Ty) : Err() {
		override fun toString() =
			"Expected a ${expected}, got a ${actual}"
	}
}

class CompileError(val kind: Err) : Exception() {
	constructor(loc: Loc, kind: Err) : this(kind) {
		this.loc = loc
	}

	var loc: Loc by Late()
	var module: Module by Late()

	override fun toString(): String {
		val locLineColumn = module.lineColumnGetter.lineAndColumnAtLoc(loc)
		return "Error at ${module.fullPath} ${locLineColumn}: $kind"
	}
}
