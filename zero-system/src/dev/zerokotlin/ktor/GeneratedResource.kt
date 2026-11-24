package dev.zerokotlin.ktor

import io.ktor.server.routing.Route

/** 早期单文件生成器的入口。新业务由各自的 Controller 声明路由。 */
internal fun Route.generatedResource(store: SystemStore, spec: ResourceSpec) {
    val service = DefaultResourceService(store, spec, JdbcResourceMapper(spec))
    registerResourceRoutes(spec.path, ResourceHttp(service), service)
}
