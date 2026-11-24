package dev.zerokotlin.ktor

/** 拒绝不存在的父节点和间接循环，调用方在写事务中传入目录快照。 */
internal fun validateParent(id: String, parent: String?, parents: Map<String, String?>) {
    var next = parent
    val visited = mutableSetOf(id)
    while (next != null) {
        if (!visited.add(next)) invalid("不能形成循环的上下级关系")
        if (next !in parents) invalid("上级节点不存在")
        next = parents[next]
    }
}
