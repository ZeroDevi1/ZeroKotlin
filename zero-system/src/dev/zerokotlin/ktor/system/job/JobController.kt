package dev.zerokotlin.ktor.system.job

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.id
import dev.zerokotlin.ktor.window
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/system/jobs。任务设置和手动执行请求进入 JobService，响应不等待后台消费。 */
internal fun Route.jobController(service: JobService) = route("jobs") {
    get { call.respond(service.list(call.bearer(), call.window())) }
    get("handlers") { call.respond(service.handlers(call.bearer())) }
    get("logs") {
        val job = call.request.queryParameters["jobId"]
        call.respond(service.logs(call.bearer(), call.window(), job))
    }
    post {
        val token = call.bearer()
        call.respond(HttpStatusCode.Created, service.create(token, call.receive<SaveJobRequest>()))
    }
    route("{id}") {
        put {
            val token = call.bearer()
            service.update(token, call.id(), call.receive<SaveJobRequest>())
            call.respond(HttpStatusCode.NoContent)
        }
        delete {
            val id = call.id()
            service.delete(call.bearer(), id)
            call.respond(HttpStatusCode.NoContent)
        }
        post("pause") {
            service.setPaused(call.bearer(), call.id(), true)
            call.respond(HttpStatusCode.NoContent)
        }
        post("resume") {
            service.setPaused(call.bearer(), call.id(), false)
            call.respond(HttpStatusCode.NoContent)
        }
        post("run") {
            val token = call.bearer()
            val id = call.id()
            call.respond(HttpStatusCode.Accepted, service.submitRun(token, id, call.receive<RunJobRequest>()))
        }
    }
}
