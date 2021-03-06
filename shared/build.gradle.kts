import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("de.fayard.dependencies")
}

val genCommonSrcKt = buildDir.resolve("gen-src/commonMain/kotlin").apply { mkdirs() }
val genBackendResource = buildDir.resolve("gen-src/backendMain/resources").apply { mkdirs() }

val releaseTime = System.getenv("HEROKU_RELEASE_CREATED_AT") ?: run {
    val now = LocalDateTime.now(ZoneOffset.UTC)
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(now)
}

val gitCommitHash = System.getenv("SOURCE_VERSION") ?: captureExec("git", "rev-parse", "HEAD").trim()

val generateConstantsTask = tasks.create("generateConstants") {
    doLast {
        logger.lifecycle("generating constants")
        generateConstants(genCommonSrcKt, "penta", "Constants") {
            field("VERSION") value "0.0.1"
            field("GIT_HASH") value gitCommitHash
            field("RELEASE_TIME") value releaseTime
        }
    }
}
val depSize = tasks.create("depSize")

tasks.withType(AbstractKotlinCompile::class.java).all {
    logger.info("registered generating constants to $this")
    dependsOn(generateConstantsTask)
}

kotlin {
    jvm() {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    js {
        nodejs()
//        useCommonJs()
//        browser {
//            runTask {
//                sourceMaps = true
//            }
//            webpackTask {
//                sourceMaps = true
//            }
//        }

        compilations.all {
            kotlinOptions {
                sourceMap = true
                sourceMapPrefix = ""
                metaInfo = true
//                moduleKind = "amd"
            }
        }
    }

    /* Targets configuration omitted.
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib"))
                api("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:_")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:_")

                api(d2v("core"))
                api(d2v("color"))

                // logging
                api("com.soywiz.korlibs.klogger:klogger:_")
                api("io.github.microutils:kotlin-logging-common:_")

                // Redux
                api("org.reduxkotlin:redux-kotlin:_")
                api("org.reduxkotlin:redux-kotlin-reselect:_")

//                api(project(":ksvg"))
//                api("com.github.nwillc:ksvg:3.0.0")
            }

            kotlin.srcDirs(genCommonSrcKt.path)
        }

        val commonTest by getting {
            dependencies {
                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))
            }
        }

        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                api(kotlin("stdlib-jdk8"))

                api("org.jetbrains.kotlinx:kotlinx-serialization-runtime:_")

                // KTOR
                api(ktor("server-core"))
                api(ktor("server-cio"))
                api(ktor("websockets"))
                api(ktor("jackson"))

                // logging
//                api("com.soywiz.korlibs.klogger:klogger-jvm:_")

                // serialization
//                api("org.jetbrains.kotlinx:kotlinx-serialization-runtime:_")

                // Jackson
                api("com.fasterxml.jackson.core:jackson-databind:_")
                api("com.fasterxml.jackson.module:jackson-module-kotlin:_")

                // logging
                api("ch.qos.logback:logback-classic:_")
                api("io.github.microutils:kotlin-logging:_")
            }
            kotlin.srcDirs(genBackendResource.path)
        }

        jvm().compilations["test"].defaultSourceSet {
            dependencies {
                api(kotlin("test"))
                api(kotlin("test-junit"))
            }
        }

        val commonClient by creating {
            dependsOn(commonMain)
            dependencies {
                api(d2v("viz"))

                api(ktor("client-core"))
                api(ktor("client-json"))
                api(ktor("client-serialization"))

//                api(ktor("client-websocket"))
            }
        }

        js().compilations["main"].defaultSourceSet {
            dependsOn(commonClient)
            dependencies {
                // cannot look up serialzation utils otherwise
                api("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:_")
                // logging
                api("com.soywiz.korlibs.klogger:klogger-js:_")
                api("io.github.microutils:kotlin-logging-js:_")

                // ktor client
                api(ktor("client-core-js"))
                api(ktor("client-json-js"))
                api(ktor("client-serialization-js"))

                api(npm("react", "^16.9.0"))
                api(npm("react-dom", "^16.9.0"))
//                api(npm("react-router-dom"))
                api(npm("styled-components", "^4.4.1"))
                api(npm("inline-style-prefixer", "^5.1.0"))
                api(npm("core-js", "^3.4.7"))
                api(npm("css-in-js-utils", "^3.0.2"))
                api(npm("redux", "^4.0.0"))
                api(npm("react-redux", "^5.0.7"))

                // temp fix ?
                api(npm("text-encoding"))
                api(npm("abort-controller"))

                api(npm("redux-logger"))

                val kotlinWrappersVersion = "pre.90-kotlin-1.3.61"
                api("org.jetbrains:kotlin-react:16.9.0-${kotlinWrappersVersion}")
                api("org.jetbrains:kotlin-react-dom:16.9.0-${kotlinWrappersVersion}")
                api("org.jetbrains:kotlin-css:1.0.0-${kotlinWrappersVersion}")
                api("org.jetbrains:kotlin-css-js:1.0.0-${kotlinWrappersVersion}")
                api("org.jetbrains:kotlin-styled:1.0.0-${kotlinWrappersVersion}")

                api("org.jetbrains:kotlin-redux:4.0.0-${kotlinWrappersVersion}")
                api("org.jetbrains:kotlin-react-redux:5.0.7-${kotlinWrappersVersion}")

//                api("org.jetbrains:kotlin-react:_")
//                api("org.jetbrains:kotlin-react-dom:_")
//                api("org.jetbrains:kotlin-css:_")
//                api("org.jetbrains:kotlin-css-js:_")
//                api("org.jetbrains:kotlin-styled:_")

//                api("org.jetbrains:kotlin-redux:_")
//                api("org.jetbrains:kotlin-react-redux:_")

                // material UI components
                api(project(":muirwik"))
//                api(project(":muirwik"))

                api("com.github.nwillc:ksvg-js:3.0.0")
            }
        }

        js().compilations["test"].defaultSourceSet {
            dependencies {
//                api(kotlin("test"))
            }
        }
    }
}

