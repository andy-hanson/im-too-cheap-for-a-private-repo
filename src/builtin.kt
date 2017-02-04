import u.*
import n.*

private val members = HashMap<Sym, Member>()

private fun ty(name: String, ty: Ty) {
	ty(name.sym, ty)
}
private fun ty(name: Sym, ty: Ty) {
	members.add(name, ty)
}

internal object Builtin {
	init {
		for (p in Prim.all) {
			ty(p.name, p)
		}
	}

	val allMembers: Lookup<Sym, Member> = Lookup.fromHashMap(members)
}
