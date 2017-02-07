package compile.parse

fun inRange(ch: Char, min: Char, max: Char): Boolean =
	min <= ch && ch <= max

fun isDigit(ch: Char): Boolean =
	inRange(ch, '0', '9')

fun isOperatorChar(ch: Char): Boolean =
	when (ch) {
		'@', '+', '-', '*', '/', '^', '?', '<', '>', '=' -> true
		else -> false
	}

fun isNameStartChar(ch: Char): Boolean =
	inRange(ch, 'a', 'z') || inRange(ch, 'A', 'Z')

fun isNameChar(ch: Char): Boolean =
	isNameStartChar(ch) || isDigit(ch) || isOperatorChar(ch)
