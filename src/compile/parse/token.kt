package compile.parse

import ast.LiteralValue
import u.*

sealed class Token {
	class Name(val name: Sym) : Token() {
		override fun toString() = "Name($name)"
	}
	class TyName(val name: Sym) : Token() {
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

	object At : Kw("@")
	object AtAt : Kw("@@")
	object Backslash : Kw("\\")
	object Def : Kw("def")
	object Enum : Kw("enum")
	object Fun : Kw("fun")
	object Generic : Kw("generic")
	object Import : Kw("import")
	object Slots : Kw("slots")
	object Val : Kw("val")
	object Var : Kw("var")

	abstract class Kw(name: String) : Token() {
		companion object {
			//Just the ones that
			private val all: Arr<Kw> by lazy {
				Arr.of(At, AtAt, Backslash, Def, Enum, Fun, Generic, Import, Slots, Val, Var)
			}
			private val nameToKw: Map<Sym, Kw> by lazy {
				mapFromValues(all, Token.Kw::name)
			}
			fun opKeyword(name: Sym): Kw? =
				nameToKw[name]
		}

		val name = name.sym

		override fun toString() =
			name.str

	}

	// Keyword that does *not* resemble an identifier. We want toString() to look nice.
	abstract class PlainKw(val name: String) : Token() {
		override fun toString() =
			name
	}

	// Grouping
	object Underscore : PlainKw("_")
	object Equals : PlainKw("=")
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
	object DotDot : PlainKw("")

}
