package dev.zerokotlin.ktor

import io.ktor.server.routing.Route

/** 业务模块一次声明 Mapper 和 Controller，并用同名 Flyway 模块开关启用。 */
internal class BusinessModule(
    val name: String,
    val mappers: Set<Class<*>>,
    val register: Route.(SystemStore) -> Unit,
)

/** 在这里显式装配生成的业务 Controller 及其 ServiceImpl。 */
internal val businessModules: List<BusinessModule> = listOf(
    // BusinessModule("orders", setOf(OrderMapper::class.java)) { store ->
    //     registerOrderRoutes(OrderServiceImpl(store))
    // },
)

internal fun Route.businessRoutes(store: SystemStore, modules: List<BusinessModule>) {
    modules.forEach { it.register(this, store) }
}
