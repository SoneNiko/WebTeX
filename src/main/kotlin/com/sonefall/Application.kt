package com.sonefall

import com.sonefall.plugins.configureRouting
import dev.inmo.krontab.doWhile
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

val configLocation = Path("config.json")
val config = readOrCreateConfig(configLocation)

// function to recursively add files to the config
fun addFilesRecursively(directory: Path) {
    val files = SystemFileSystem.list(directory)
    files.forEach { file ->
        if (SystemFileSystem.metadataOrNull(file)!!.isDirectory) {
            addFilesRecursively(file)
        } else {

            val conf = RouteConfig(
                generationConfig = config.defaultRouteSettings.generationConfig,
                path = "/" + file.toString().replace("${config.pathConfig.tex}\\", "").replace("\\", "/")
                    .replace(".tex", ""),
                file = file.toString().replace("${config.pathConfig.tex}\\", "")
            )

            if (config.routesOverride.any { it.file == conf.file }) return@forEach
            config.routesOverride.add(conf)
        }
    }
}


suspend fun main() {
    // add all files in the tex directory to the config
    addFilesRecursively(Path(config.pathConfig.tex))

    println(Json { prettyPrint = true }.encodeToString(config))

    File(config.pathConfig.temp).deleteRecursively()
    SystemFileSystem.createDirectories(Path(config.pathConfig.temp + "\\serve"))
    SystemFileSystem.createDirectories(Path(config.pathConfig.temp + "\\builds"))


    // build all static and rebuilding routes
    config.routesOverride.forEach { routeConfig ->

        if (routeConfig.file == null) return@forEach
        val toCreate = Path(routeConfig.file).parent

        if (toCreate != null) {
            SystemFileSystem.createDirectories(
                Path(
                    "${config.pathConfig.temp}/serve/${toCreate}"
                )
            )
        }


        SystemFileSystem.source(Path("${config.pathConfig.tex}/${routeConfig.file}")).buffered().use { source ->
            SystemFileSystem.sink(Path("${config.pathConfig.temp}\\serve\\${routeConfig.file}")).buffered()
                .use { sink ->
                    sink.writeString(source.readString())
                }
        }

    }

    // build the files for the static routes in the tmp directory with the following command
    // lualatex path/to/file.tex
    config.routesOverride.forEach { routeConfig ->
        if (routeConfig.generationConfig.type == RouteGenerationType.STATIC || routeConfig.generationConfig.type == RouteGenerationType.REBUILD_INTERVAL) {
            build(routeConfig)
        }
    }

    config.routesOverride.filter { it.generationConfig.type == RouteGenerationType.REBUILD_INTERVAL }.forEach {
        val scope = CoroutineScope(Dispatchers.Default)

        scope.launch {
            doWhile("*/${it.generationConfig.interval ?: (60 * 5)} * * * * *") {time ->
                build(it)
                true
            }
        }
    }

    // Start the server
    embeddedServer(
        Netty,
        port = config.serverConfig.port,
        host = config.serverConfig.host,
        module = Application::module
    ).start(wait = true)
}

private fun build(it: RouteConfig) {
    println("Building route ${it.path}")
    val parent = Path(it.file ?: "_notfound").parent ?: ""

    val command =
        "lualatex -interaction=nonstopmode --output-directory=${config.pathConfig.temp}\\serve\\${parent} ${config.pathConfig.temp}\\serve\\${it.file} "
    val process = Runtime.getRuntime().exec(command)

    process.waitFor()
    println("Built route ${it.path}")
}

private fun readOrCreateConfig(configLocation: Path): Config {
    val mapper = Json { prettyPrint = true }

    if (!SystemFileSystem.exists(configLocation)) {
        val configObject = Config(
            serverConfig = ServerConfig(),
            pathConfig = PathsConfig(),
            defaultRouteSettings = RouteConfig(
                generationConfig = RouteGenerationConfig(
                    type = RouteGenerationType.DYNAMIC,
                    interval = 0
                )
            ),
            routesOverride = emptyList<RouteConfig>().toMutableList(),
        )
        val result = mapper.encodeToString(Config.serializer(), configObject)
        SystemFileSystem.sink(configLocation).buffered().use {
            it.writeString(result)
        }
    }

    val config = SystemFileSystem.source(configLocation).buffered().use {
        mapper.decodeFromString(Config.serializer(), it.readString())
    }
    return config
}

fun Application.module() {
    configureRouting(config)
}
