package ast

import u.*

abstract class Ast : HasSexpr {
	abstract val loc: Loc
}

class Module(override val loc: Loc, val imports: Arr<Import>, val klass: Klass) : Ast() {
	override fun toSexpr(): Sexpr =
		sexpr("Module", sexpr("import", sexpr(imports)), klass)

	class Import(override val loc: Loc, val path: ImportPath) : Ast() {
		override fun toSexpr() =
			path.toSexpr()

		sealed class ImportPath : HasSexpr {
			class Global(val path: Path) : ImportPath() {
				override fun toSexpr() =
					path.toSexpr()
			}

			class Relative(val path: RelPath) : ImportPath() {
				override fun toSexpr() =
					path.toSexpr()
			}
		}
	}
}



//TODO: may be generic<T>
//TODO: may be union instead of having state
data class Klass(override val loc: Loc, val name: Sym, val head: Head, val members: Arr<Member>): Ast() {
	override fun toSexpr() =
		sexpr("Klass", name, head, sexpr(members))

	//Head: may be data+state or union (but not both.)
	sealed class Head : Ast() {
		data class Slots(override val loc: Loc, val vars: Arr<Slot>) : Head() {
			data class Slot(override val loc: Loc, val mutable: Boolean, val ty: Ty, val name: Sym) : Ast() {
				override fun toSexpr() =
					sexpr(if (mutable) "var" else "val", ty, name)
			}

			override fun toSexpr() =
				sexpr("Record", vars)
		}
	}
}

sealed class Member() : Ast() {
	abstract val name: Sym
}

data class Method(
	override val loc: Loc,
	val isStatic: Boolean,
	val returnTy: Ty,
	override val name: Sym,
	val parameters: Arr<Parameter>,
	val body: Expr) : Member() {

	override fun toSexpr() =
		sexpr(if (isStatic) "fun" else "def") {
			s(returnTy)
			s(name)
			s(parameters)
			s(body)
		}

	data class Parameter(override val loc: Loc, val ty: Ty, val name: Sym) : Ast() {
		override fun toSexpr(): Sexpr =
			sexprTuple(ty, name)
	}
}

sealed class NamespaceId : Ast() {
	data class Access(override val loc: Loc, val name: Sym) : NamespaceId() {
		override fun toSexpr() =
			TODO()
	}
	data class PrefixedAccess(override val loc: Loc, val namespace: NamespaceId, val name: Sym) : NamespaceId() {
		override fun toSexpr() =
			TODO()
	}
}

sealed class Ty : Ast() {
	data class Access(override val loc: Loc, val name: Sym) : Ty() {
		override fun toSexpr() = Sexpr.S(name)
	}
	//data class PrefixedAccess(override val loc: Loc, val namespace: NamespaceId, val name: Sym): Ty() {
	//	override fun toSexpr() = TODO()
	//}
	data class Inst(override val loc: Loc, val instantiated: Access, val tyArgs: Arr<Ty>) : Ty() {
		override fun toSexpr() =
			sexprTuple(Arr.cons(instantiated, tyArgs))
	}
}

sealed class Expr : Ast()

data class Access(override val loc: Loc, val name: Sym) : Expr() {
	override fun toSexpr() =
		Sexpr.S(name)
}
data class StaticAccess(override val loc: Loc, val className: Sym, val staticMethodName: Sym) : Expr() { //Unlike Ty.Access, this is an Expr.
	override fun toSexpr() =
		sexprTuple(className, staticMethodName)
}

data class OperatorCall(override val loc: Loc, val left: Expr, val operator: Sym, val right: Expr) : Expr() {
	override fun toSexpr() = sexprTuple(operator, left, right)
}

data class Call(override val loc: Loc, val target: Expr, val args: Arr<Expr>) : Expr() {
	override fun toSexpr() = sexpr("Call") {
		s(target)
		for (arg in args)
			s(arg)
	}
}

data class GetProperty(override val loc: Loc, val target: Expr, val propertyName: Sym) : Expr() {
	override fun toSexpr() =
		sexpr("GetProperty", target, propertyName)
}

data class Let(override val loc: Loc, val assigned: Pattern, val value: Expr, val then: Expr) : Expr() {
	override fun toSexpr() =
		sexpr("Let", sexprTuple(assigned, value), then)
}

data class Seq(override val loc: Loc, val first: Expr, val then: Expr) : Expr() {
	override fun toSexpr() =
		sexpr("Seq", first, then)
}

data class Literal(override val loc: Loc, val value: LiteralValue) : Expr() {
	override fun toSexpr() = value.toSexpr()
}

sealed class LiteralValue : HasSexpr {
	data class Int(val value: Long) : LiteralValue() {
		override fun toSexpr() =
			Sexpr.N(value)
	}
	data class Float(val value: Double): LiteralValue() {
		override fun toSexpr() =
			Sexpr.F(value)
	}
	data class Str(val value: String) : LiteralValue() {
		override fun toSexpr() =
			Sexpr.Str(value)
	}
}


sealed class Pattern : Ast() {
	class Ignore(override val loc: Loc) : Pattern() {
		override fun toSexpr() =
			sexpr("Ignore")
	}
	data class Single(override val loc: Loc, val name: Sym) : Pattern() {
		override fun toSexpr() =
			Sexpr.S(name)
	}
	data class Destruct(override val loc: Loc, val destructed: Arr<Pattern>) : Pattern() {
		override fun toSexpr() =
			sexpr(destructed)
	}
}


//lambda

//cs, ts

//list

