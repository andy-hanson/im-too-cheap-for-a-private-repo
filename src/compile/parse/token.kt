package compile.parse

import ast.LiteralValue
import u.*

sealed class Token {
	class Name(val name: Sym) : Token() {
		override fun toString() = "Name($name)"
	}
	class Operator(val name: Sym) : Token() {
		override fun toString() = "Operator($name)"
	}
	class Literal(val value: LiteralValue) : Token() {
		override fun toString() = value.toString()
	}
	class QuoteStart(val head: String) : Token() {
		override fun toString() = "QuoteStart(\"$head\"')"
	}


	abstract class Kw(name: String) : Token() {
		companion object {
			private val all = Arr.of(At, AtAt, Backslash, Equals, Import, Underscore, Fun, Def)
			private val nameToKw = Lookup.fromValues(all, Kw::name)
			fun opKeyword(name: Sym): Kw? =
				nameToKw[name]
		}

		val name = name.sym

		override fun toString() =
			name.str

	}
	object At : Kw("@")
	object AtAt : Kw("@@")
	object Backslash : Kw("\\")
	object Equals : Kw("=")
	object Import : Kw("import")
	object Underscore : Kw("_")
	object Fun : Kw("fun")
	object Def : Kw("def")

	// Keyword that does *not* resemble an identifier. We want toString() to look nice.
	abstract class PlainKw(val name: String) : Token() {
		override fun toString() =
			name
	}

	// Grouping
	object Indent : PlainKw("->")
	object Dedent : PlainKw("<-")
	object Newline : PlainKw("\\n")
	object Lparen : PlainKw("(")
	object Rparen : PlainKw(")")
	object Lbracket : PlainKw("[")
	object Rbracket : PlainKw("]")
	object LCurly : PlainKw("{")
	object RCurly : PlainKw("}")
	object EOF : PlainKw("EOF")

	// Punctuation
	object Colon : PlainKw(":")
	object Comma : PlainKw(",")
	object Dot : PlainKw(".")
	object DotDot : PlainKw("..")

}
