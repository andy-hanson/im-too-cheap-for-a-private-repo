package compile.check

import compile.err.*
import u.*
import n.*



class Checker {
	/*
Here's what you can access:

* Local variables to a closure
* Parameters to a closure
* Local variables to a function
* Parameters to a function (incl. type parameters)
* Entities in the current class (incl. its type parameters, slots, methods)
* Imports

Currently, classes cannot define inner types. So we get those early!

*/

	/*
SO:
base scope (constant throughout entire module) is:

* Imports
* Current class members


	 */

	private val imports: Lookup<Sym, NzClass> = TODO()

	private fun check(path: Path, fullPath: Path, m: ast.Module): Module {
		//TODO: use m.imports
		val klass = emptyClass(m.klass)

		val m = Module(path, fullPath, klass)
	}

	private fun emptyClass(k: ast.Class): NzClass {
		val head = when (k.head) {
			is ast.Class.Head.Record -> {
				NzClass.Head.Record(k.head.loc, k.head.vars.map {
					NzClass.Head.Record.Var(it.loc, it.mutable, getTy(it.ty), it.name)
				})
			}
		}
		val members = Lookup.beeld<Sym, Member> {
			for (memberAst in k.members) {
				val name = memberAst.name
				addOrFail(name, emptyMember(memberAst)) { Error("Duplicate member name $name") }
			}
		}
		return NzClass(k.name, head, members)
	}

	private fun emptyMember(m: ast.Member) =
		when (m) {
			is ast.Method -> {
				Method(m.isStatic, getTy(m.returnTy), m.parameters.map { Method.Parameter(getTy(it.ty), it.name) })
			}
		}

	private fun getTy(ast: ast.Ty): Ty =
		when (ast) {
			is ast.Ty.Access -> {
				accessTy(ast.loc, ast.name)
			}
			is ast.Ty.Inst -> {
				accessTy(ast.instantiated.loc, ast.instantiated.name)
				ast.tyArgs.map(this::getTy)
				TODO("!!")
			}
		}

	private fun accessTy(loc: Loc, name: Sym): Ty {
		val klass = imports[name]
		if (klass != null)
			return klass

		val builtin = Builtin.allMembers[name] ?: raise(loc, Err.CantBind(name))
		return when (builtin) {
			is Ty -> builtin
			else -> TODO("err")
		}
	}
}

private class BaseScope() {

}


