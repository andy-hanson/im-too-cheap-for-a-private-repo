package compile.parse

import n.LiteralValue
import u.*
import compile.err.*
import kotlin.text.slice

private val EOF: Char = (-1).toChar()

internal abstract class Lexer(preSource: String) {
	//Ensure ends in newline
	//Ensure last character is EOF
	private val source = (if (preSource.endsWith("\n")) preSource else preSource + "\n") + EOF

	// Index of the character we are *about* to take.
	private var pos = 0

	private var indent: Int = 0
	// Number of Token.Dedent we have to output before continuing to read
	private var dedenting: Int = 0

	init {
		skipNewlines()
	}

	//Don't use?
	protected fun curPos(): Pos =
		pos

	protected fun locFrom(start: Pos): Loc =
		Loc(start, pos)

	private val peek
		get() = source[pos]

	private fun readChar(): Char =
		source[pos].also { pos++ }

	private fun skip() {
		readChar()
	}

	private inline fun skipWhile(pred: (Char) -> Boolean) {
		require(!pred(EOF)) // Else this will be an infinite loop
		if (pred(peek)) {
			run {
				var ch: Char
				do {
					pos += 1
					ch = source[pos]
				} while (pred(ch))
				ch
			}
		}
	}

	private fun skipNewlines() =
		skipWhile { it == '\n' }

	data class QuotePart(val text: String, val isEndOfQuote: Boolean)
	protected fun nextQuotePart(): QuotePart {
		val (text, isEnd) = buildStringFromChars { addChar ->
			var isEnd: Boolean
			outer@ while (true) {
				val ch = readChar()
				when (ch) {
					'"' -> {
						isEnd = true
						break@outer
					}
					'{' -> {
						isEnd = false
						break@outer
					}
					'\n' ->
						TODO("Compile error: unterminated quote")
					'\\' ->
						addChar(escape(readChar()))
					else ->
						addChar(ch)
				}
			}
			isEnd
		}
		return QuotePart(text, isEnd)
	}


	//TODO: just slice!
	private inline fun bufferWhile(addChar: (Char) -> Unit, pred: (Char) -> Boolean) {
		if (pred(peek)) {
			addChar(peek);
			// Returns the first char that's not skipped.
			//TODO:NEATER?
			var ch: Char
			while (true) {
				ch = source[pos]
				pos += 1
				if (!pred(ch))
					break
				addChar(ch)
			}
		}
	}

	private fun takeNumber(negate: Boolean, fst: Char): Token {
		val (str, isFloat) = buildStringFromChars { addChar ->
			addChar(fst)
			bufferWhile(addChar, ::isDigit)
			(peek == '.').also {
				if (it) {
					skip()
					addChar('.')
					must(isDigit(peek), Loc.singleChar(pos), Err.TooMuchIndent)
					bufferWhile(addChar, ::isDigit)
				}
			}
		}
		val value =
			if (isFloat) {
				val f = str.toDouble()
				LiteralValue.Float(if (negate) -f else f)
			}
			else {
				val i = str.toInt()
				LiteralValue.Int(if (negate) -i else i)
			}
		return Token.Literal(value)
	}

	private inline fun buildSymbol(startPos: Int, pred: (Char) -> Boolean): Sym {
		while (pred(peek)) {
			pos++
		}
		return source.slice(startPos until pos).sym
	}

	private fun takeOperator(startPos: Int): Token =
		takeNameToken(startPos, ::isOperatorChar) { Token.Operator(it) }

	private inline fun countWhile(pred: (Char) -> Boolean): Int {
		var count = 0
		while (pred(peek)) {
			skip()
			count++
		}
		return count
	}

	private fun lexIndent(): Int {
		val start = pos
		return countWhile { it == '\t' }.also {
			must(peek != ' ', locFrom(start), Err.LeadingSpace)
		}
	}

	private fun handleNewline(indentOnly: Boolean): Token {
		skipNewlines()
		val oldIndent = indent
		indent = lexIndent()
		return when {
			indent > oldIndent -> {
				must(indent == oldIndent + 1, Loc.singleChar(pos), Err.TooMuchIndent)
				Token.Indent
			}
			indent == oldIndent ->
				if (indentOnly)
					nextToken()
				else
					Token.Newline
			else -> {
				dedenting = oldIndent - indent - 1
				Token.Dedent
			}
		}
	}

	protected fun nextToken(): Token {
		if (dedenting != 0) {
			dedenting = dedenting - 1
			return Token.Dedent
		} else
			return takeNext()
	}

	private fun takeNext(): Token {
		val startPos = pos
		val ch = readChar()
		return when (ch) {
			EOF -> {
				// Remember to dedent before finishing
				if (indent != 0) {
					indent--
					Token.Dedent
				} else
					Token.EOF
			}

			' ' -> {
				must(peek != '\n', Loc.singleChar(pos), Err.TrailingSpace)
				takeNext()
			}

			'\n' ->
				handleNewline(false)

			'|' -> {
				skipWhile { it != '\n' }
				handleNewline(true)
			}

			'\\' -> Token.Backslash
			':' -> Token.Colon
			'(' -> Token.Lparen
			')' -> Token.Rparen
			'[' -> Token.Lbracket
			']' -> Token.Rbracket
			'{' -> Token.LCurly
			'}' -> Token.RCurly
			'_' -> Token.Underscore

			'-' -> {
				val next = readChar()
				if (isDigit(next))
					takeNumber(true, next)
				else
					takeOperator(startPos)
			}

			'.' ->
				when (peek) {
					'.' -> {
						skip()
						Token.DotDot
					}
					else ->
						Token.Dot
				}

			'"' -> {
				val (str, isDone) = nextQuotePart()
				if (isDone)
					Token.Literal(LiteralValue.Str(str))
				else
					Token.QuoteStart(str)
			}

			in '0' .. '9' ->
				takeNumber(false, ch)
			in 'a' .. 'z' ->
				takeNameToken(startPos, ::isNameChar) { Token.Name(it) }
			in 'A' .. 'Z' ->
				takeNameToken(startPos, ::isNameChar) { Token.TyName(it) }
			'@', '+', '*', '/', '^', '?', '<', '>', '=' ->
				takeOperator(startPos)

			else ->
				raise(Loc.singleChar(startPos), Err.UnrecognizedCharacter(ch))
		}
	}

	protected fun tryTakeNewline(): Boolean {
		if (!tryTake('\n')) {
			return false
		}

		// Allow many blank lines.
		while (tryTake('\n')) {}

		repeat(this.indent) {
			expectCharacter('\t')
		}

		return true
	}


	protected fun takeSpace() {
		expectCharacter(' ')
	}
	protected fun takeLparen() {
		expectCharacter('(')
	}
	protected fun takeComma() {
		expectCharacter(',')
	}
	protected fun takeDot() {
		expectCharacter('.')
	}
	protected fun tryTakeRparen() = tryTake(')')
	protected fun tryTakeDot() = tryTake('.')

	protected fun takeKeyword(kw: Token.Kw) {
		val start = curPos()
		val name = takeName()
		if (name != kw.name) {
			raise(locFrom(start), Err.ExpectedKeyword(kw))
		}
	}

	private inline fun takeNameToken(startPos: Int, pred: (Char) -> Boolean, makeToken: (Sym) -> Token): Token {
		val name = buildSymbol(startPos, pred)
		return Token.Kw.opKeyword(name) ?: makeToken(name)
	}

	private fun tryTake(ch: Char): Boolean {
		if (peek == ch) {
			skip()
			return true
		}
		return false
	}

	protected fun takeKeyword(): Token.Kw {
		val start = pos
		expectCharacter("keyword") { it in 'a' .. 'z' }
		val name = buildSymbol(start, ::isNameChar)
		return Token.Kw.opKeyword(name) ?: unexpected(start, Token.Name(name))
	}

	protected fun takeName(): Sym {
		val start = pos
		expectCharacter("non-type name") { it in 'a' .. 'z' }
		return buildSymbol(start, ::isNameChar)
	}

	protected fun takeTyName(): Sym {
		val start = pos
		expectCharacter("type name") { it in 'A' .. 'Z' }
		return buildSymbol(start, ::isNameChar)
	}

	protected fun takeIndent() {
		this.indent++
		expectCharacter('\n')
		repeat(this.indent) {
			expectCharacter('\t')
		}
	}


	data class PosNext(val pos: Pos, val next: Token)
	protected fun posNext(): PosNext {
		val p = pos
		return PosNext(p, nextToken())
	}

	fun escape(escaped: Char) =
		when (escaped) {
			'"', '{' -> escaped
			'n' -> '\n'
			't' -> '\t'
			else -> TODO("Compile error: bad escape")
		}

	private fun expectCharacter(char: Char): Unit {
		val ch = readChar()
		if (ch != char)
			raise(Loc.singleChar(pos), Err.UnexpectedCharacter(ch, "$char"))
	}

	private fun expectCharacter(explanation: String, pred: (Char) -> Boolean) {
		val ch = readChar()
		if (!pred(ch))
			raise(Loc.singleChar(pos), Err.UnexpectedCharacter(ch, explanation))
	}

	protected fun unexpected(start: Pos, token: Token): Nothing =
		raise(locFrom(start), Err.Unexpected(token))
}
