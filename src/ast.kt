package ast

import u.*

abstract class Ast : HasSexpr {
	abstract val loc: Loc
}


//TODO: may be generic<T>
//TODO: may be union instead of having state
data class Class(override val loc: Loc, val name: Sym, val head: Head, val members: Arr<Member>): Ast() {
	override fun toSexpr(): Sexpr {
		return sexpr("Class", name)
	}

	//Head: may be data+state or union (but not both.)
	sealed class Head : Ast() {
		data class Record(override val loc: Loc, val vars: Arr<Var>) : Head() {
			data class Var(override val loc: Loc, val mutable: Bool, val ty: Ty, val name: Sym) : Ast {
				override fun toSexpr() =
					sexpr(if (mutable) "var" else "val", ty, name)
			}

			override fun toSexpr() =
				sexpr("Record", vars)
		}
	}
}

sealed class Member() : Ast() {}
data class Fn(
	override val loc: Loc,
	val isStatic: Bool,
	val returnTy: Ty,
	val name: Sym,
	val args: Arr<Arg>,
	val body: Expr) : Member() {

	override fun toSexpr() =
		TODO("!")

	data class Arg(override val loc: Loc, val ty: Ty, val name: Sym) : Ast() {
		override fun toSexpr(): Sexpr =
			TODO("!")
	}
}

sealed class Ty : HasSexpr {
	abstract val loc: Loc

	data class Access(override val loc: Loc, val name: Sym) : Ty() {
		override fun toSexpr() = Sexpr.S(name)
	}
	data class Inst(override val loc: Loc, val instantiated: Access, val tyArgs: Arr<Ty>) : Ty() {
		override fun toSexpr() =
			sexprTuple(Arr.cons(instantiated, tyArgs))
	}
}

sealed class Expr : Ast() {}

data class Access(override val loc: Loc, val name: Sym) : Expr() {
	override fun toSexpr() =
		TODO("!")
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

data class Let(override val loc: Loc, val assigned: Sym, val value: Expr, val then: Expr) : Expr() {
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


//lambda

//cs, ts

//list

