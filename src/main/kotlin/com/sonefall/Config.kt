package com.sonefall

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val serverConfig: ServerConfig,
    val pathConfig: PathsConfig,
    val defaultRouteSettings: RouteConfig,
    val routesOverride: MutableList<RouteConfig>
)

@Serializable
data class PathsConfig(
    val temp: String = ".tmp",
    val tex: String = "serve"
)

@Serializable
data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0"
)

@Serializable
data class RouteConfig(
    val generationConfig: RouteGenerationConfig,
    val path: String? = null,
    val file: String? = null,
    val customBuildCommand: String? = null
)

@Serializable
data class RouteGenerationConfig(
    val type: RouteGenerationType,
    val interval: Int? = null
)

@Serializable
enum class RouteGenerationType {
    STATIC,  // Static routes are generated once and never change
    DYNAMIC,  // SSR
    REBUILD_INTERVAL // ISR
}
