package dev.zerokotlin.ktor.system.dictionary

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.DeleteBatch
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.id
import dev.zerokotlin.ktor.missing
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/system/dictionaries。登录用户可读启用的选项，管理接口额外校验对应权限。 */
internal fun Route.dictionaryController(service: DictionaryService) = route("dictionaries") {
    get { call.respond(service.list(call.bearer())) }
    get("by-code/{code}/items") {
        val token = call.bearer()
        call.respond(service.byCode(token, call.parameters["code"]))
    }
    post("by-codes/items") {
        val token = call.bearer()
        call.respond(service.byCodes(token, call.receive<DictionaryCodes>().codes))
    }
    post {
        val token = call.bearer()
        call.respond(HttpStatusCode.Created, service.create(token, call.receive<NewDictionary>()))
    }
    put("{id}") {
        val token = call.bearer()
        service.update(token, call.id(), call.receive<EditDictionary>())
        call.respond(HttpStatusCode.NoContent)
    }
    delete("{id}") {
        service.delete(call.bearer(), listOf(call.id()))
        call.respond(HttpStatusCode.NoContent)
    }
    post("batch-delete") {
        val token = call.bearer()
        service.delete(token, call.receive<DeleteBatch>().ids)
        call.respond(HttpStatusCode.NoContent)
    }
    route("{dictionaryId}/items") {
        get {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            call.respond(service.items(token, dictionary))
        }
        post {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            call.respond(HttpStatusCode.Created, service.createItem(token, dictionary, call.receive<EditItem>()))
        }
        put("{id}") {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            service.updateItem(token, dictionary, call.id(), call.receive<EditItem>())
            call.respond(HttpStatusCode.NoContent)
        }
        delete("{id}") {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            service.deleteItems(token, dictionary, listOf(call.id()))
            call.respond(HttpStatusCode.NoContent)
        }
        post("batch-delete") {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            service.deleteItems(token, dictionary, call.receive<DeleteBatch>().ids)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
