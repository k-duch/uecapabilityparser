package it.smartphonecombo.uecapabilityparser.server

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder
import io.javalin.core.compression.Gzip
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.staticfiles.Location
import io.javalin.plugin.json.JsonMapper
import it.smartphonecombo.uecapabilityparser.extension.getArray
import it.smartphonecombo.uecapabilityparser.extension.getString
import it.smartphonecombo.uecapabilityparser.model.combo.ComboEnDc
import it.smartphonecombo.uecapabilityparser.model.combo.ComboLte
import it.smartphonecombo.uecapabilityparser.model.combo.ComboNr
import it.smartphonecombo.uecapabilityparser.model.combo.ComboNrDc
import it.smartphonecombo.uecapabilityparser.util.Output
import it.smartphonecombo.uecapabilityparser.util.Parsing
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import org.eclipse.jetty.http.HttpStatus

class JavalinApp {
    private val base64 = Base64.getDecoder()
    private val jsonMapper =
        object : JsonMapper {
            override fun <T : Any> fromJsonString(json: String, targetClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                val deserializer = serializer(targetClass) as KSerializer<T>
                return Json.decodeFromString(deserializer, json)
            }

            override fun toJsonString(obj: Any): String {
                val serializer = serializer(obj.javaClass)
                return Json.encodeToString(serializer, obj)
            }
        }
    private val dataFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val html404 = {}.javaClass.getResourceAsStream("/web/404.html")?.readAllBytes()
    private val openapi =
        {}.javaClass
            .getResourceAsStream("/swagger/openapi.json")
            ?.bufferedReader()
            ?.readText()
            ?.replace("http://localhost:8080", "/")

    val app: Javalin =
        Javalin.create { config ->
            config.compressionStrategy(null, Gzip(4))
            config.prefer405over404 = true
            config.maxRequestSize = 100L * 1024 * 1024
            config.ignoreTrailingSlashes = true
            config.jsonMapper(jsonMapper)
            config.enableCorsForAllOrigins()
            config.addStaticFiles("/web", Location.CLASSPATH)
            config.addStaticFiles { staticFiles ->
                staticFiles.hostedPath = "/swagger"
                staticFiles.directory = "/swagger"
                staticFiles.location = Location.CLASSPATH
            }
        }

    init {
        app.exception(Exception::class.java) { e, _ -> e.printStackTrace() }
        app.error(HttpStatus.NOT_FOUND_404) { ctx ->
            if (html404 != null) {
                ctx.contentType(ContentType.HTML)
                ctx.result(html404)
            }
        }
        app.routes {
            ApiBuilder.post("/parse/0.1.0") { ctx ->
                val request = Json.parseToJsonElement(ctx.body())
                val input = request.getString("input")?.let { base64.decode(it).inputStream() }
                val inputNR = request.getString("inputNR")?.let { base64.decode(it).inputStream() }
                val inputENDC =
                    request.getString("inputENDC")?.let { base64.decode(it).inputStream() }
                val defaultNR =
                    request.getString("defaultNR")?.let { it.toBoolean() } ?: (input == null)
                val multiple0xB826 =
                    request.getString("multiple0xB826")?.let { it.toBoolean() } ?: false
                val type = request.getString("type")

                if (input == null && inputNR == null || type == null) {
                    ctx.result("Bad Request")
                    ctx.status(HttpStatus.BAD_REQUEST_400)
                } else {
                    val parsing =
                        Parsing(
                            input ?: inputNR!!,
                            inputNR,
                            inputENDC,
                            defaultNR,
                            multiple0xB826,
                            type
                        )
                    ctx.json(parsing.capabilities)
                }
            }
            ApiBuilder.post("/csv/0.1.0") { ctx ->
                val request = Json.parseToJsonElement(ctx.body())
                val type = request.getString("type")
                val input = request.getArray("input")

                if (input == null || type == null) {
                    ctx.result("Bad Request")
                    ctx.status(HttpStatus.BAD_REQUEST_400)
                } else {
                    val comboList =
                        when (type) {
                            "lteca" -> Json.decodeFromJsonElement<List<ComboLte>>(input)
                            "endc" -> Json.decodeFromJsonElement<List<ComboEnDc>>(input)
                            "nrca" -> Json.decodeFromJsonElement<List<ComboNr>>(input)
                            "nrdc" -> Json.decodeFromJsonElement<List<ComboNrDc>>(input)
                            else -> emptyList()
                        }
                    val date = dataFormatter.format(ZonedDateTime.now(ZoneOffset.UTC))
                    ctx.result(Output.toCsv(comboList))
                        .contentType("text/csv")
                        .header("Content-Disposition", "attachment; filename=${type}-${date}.csv")
                        .header("Access-Control-Expose-Headers", "Content-Disposition")
                }
            }
            ApiBuilder.get("/openapi", ::getOpenApi)
            ApiBuilder.get("/swagger/openapi.json", ::getOpenApi)
            // Add / if missing
            ApiBuilder.before("/swagger") { ctx ->
                if (!ctx.path().endsWith("/")) {
                    ctx.redirect("/swagger/")
                }
            }
        }
    }

    private fun getOpenApi(ctx: Context) {
        if (openapi != null) {
            ctx.contentType(ContentType.JSON)
            ctx.result(openapi)
        }
    }
}
