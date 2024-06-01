package com.sonefall.plugins

import com.sonefall.Config
import com.sonefall.RouteConfig
import com.sonefall.RouteGenerationType
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.io.File

fun Application.configureRouting(config: Config) {
    install(AutoHeadResponse)
    routing {
        get(".*".toRegex()) {
            val retrievedConfig = findRouteConfig(call.request.uri, config)

            if (retrievedConfig == null) {
                call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
                return@get
            }

            println(retrievedConfig.path)
            println(retrievedConfig.file)

            when (retrievedConfig.generationConfig.type) {
                RouteGenerationType.DYNAMIC -> {
                    // create a temporary directory for the build process and then serve the file from within that directory

                    // generate an id for the request
                    val requestId = generateNonce()

                    // create a temporary directory for the build process
                    val tempDir = File("${config.pathConfig.temp}/builds/$requestId")
                    tempDir.mkdirs()

                    // copy the file to the temporary directory
                    val file = File("${config.pathConfig.temp}/serve/${retrievedConfig.file!!}")
                    file.copyTo(File("${tempDir.absolutePath}/${retrievedConfig.file}"))

                    // run the custom build command
                    buildFile(tempDir, retrievedConfig)

                    val result = File("${tempDir.absolutePath}/${retrievedConfig.file.replace(".tex$".toRegex(), ".pdf")}")
                    call.servePdf(result)

                    // delete the temporary directory
                    tempDir.deleteRecursively()


                }
                else -> {
                    val file = File("${config.pathConfig.temp}/serve/${retrievedConfig.file!!.replace(".tex$".toRegex(), ".pdf")}")
                    call.servePdf(file)
                }


            }
        }
    }
}

fun buildFile(dir: File, retrievedConfig: RouteConfig) {
    val command =
        "lualatex -interaction=nonstopmode --output-directory=${dir.absolutePath} ${dir.absolutePath}\\${retrievedConfig.file} "

    val process = Runtime.getRuntime().exec(command, null, dir)
    process.waitFor()
}

suspend fun ApplicationCall.servePdf(file: File) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Inline
            .withParameter(
                ContentDisposition.Parameters.FileName,
                file.name
            )
            .toString()
    )

    response.header(
        HttpHeaders.ContentType,
        ContentType.Application.Pdf.toString()
    )

    respondFile(file)
}


fun findRouteConfig(path: String, config: Config): RouteConfig? {
    return config.routesOverride.find { it.path == path }
}