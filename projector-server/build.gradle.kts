/*
 * Copyright (c) 2019-2021, JetBrains s.r.o. and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. JetBrains designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact JetBrains, Na Hrebenech II 1718/10, Prague, 14000, Czech Republic
 * if you need additional information or have any questions.
 */

import java.net.URL
import java.util.*
import java.util.zip.ZipFile
import kotlin.streams.toList

plugins {
  kotlin("jvm")
  `maven-publish`
  application
}

application {
  mainClassName = "org.jetbrains.projector.server.ProjectorLauncher"
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
  }
}

configurations.all {
  resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

val projectorClientGroup: String by project
val projectorClientVersion: String by project
val mockitoKotlinVersion: String by project
val kotlinVersion: String by project

dependencies {
  implementation("$projectorClientGroup:projector-common:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-server-core:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-util-loading:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-util-logging:$projectorClientVersion")
  implementation("$projectorClientGroup:projector-util-loading:$projectorClientVersion")
  api(project(":projector-awt"))

  testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
  testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
}

tasks.withType<Jar> {
  manifest {
    attributes(
      "Main-Class" to application.mainClassName
    )
  }
  duplicatesStrategy = DuplicatesStrategy.WARN
}

var relayURL: String = null.toString()
var serverId: String = null.toString()
var serverTargetClasspath: String = null.toString()
var serverClassToLaunch: String = null.toString()
var ideaPath: String = null.toString()

rootProject.file("local.properties").let {
  if (it.canRead()) {
    val relayURL = Properties().apply { load(it.inputStream()) }.getProperty("ORG_JETBRAINS_PROJECTOR_SERVER_RELAY_URL") ?: null.toString()
    serverId = Properties().apply { load(it.inputStream()) }.getProperty("ORG_JETBRAINS_PROJECTOR_SERVER_RELAY_SERVER_ID")
               ?: null.toString()
    serverTargetClasspath = Properties().apply { load(it.inputStream()) }.getProperty("projectorLauncher.targetClassPath")
                            ?: null.toString()
    serverClassToLaunch = Properties().apply { load(it.inputStream()) }.getProperty("projectorLauncher.classToLaunch") ?: null.toString()
    ideaPath = Properties().apply { load(it.inputStream()) }.getProperty("projectorLauncher.ideaPath") ?: null.toString()
  }
}

var relayArgs: List<String> = emptyList()

if (relayURL != null.toString() && serverId != null.toString()) {
  relayArgs = listOf("-DORG_JETBRAINS_PROJECTOR_SERVER_RELAY_URL=$relayURL", "-DORG_JETBRAINS_PROJECTOR_SERVER_RELAY_SERVER_ID=$serverId")
}

println("----------- Server launch config ---------------")
println("Classpath: $serverTargetClasspath")
println("ClassToLaunch: $serverClassToLaunch")
println("------------------------------------------------")
if (serverTargetClasspath != null.toString() && serverClassToLaunch != null.toString()) {
  task("runServer", JavaExec::class) {
    group = "projector"
    classpath(sourceSets.main.get().runtimeClasspath, tasks.jar, serverTargetClasspath)
    jvmArgs = listOf(
      "-Dorg.jetbrains.projector.server.classToLaunch=$serverClassToLaunch",
      "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    ) + relayArgs
  }
}

println("----------- Idea launch config ---------------")
println("Idea path: $ideaPath")
println("------------------------------------------------")
if (ideaPath != null.toString()) {
  val ideaLib = "$ideaPath/lib"
  val ideaClassPath = "$ideaLib/bootstrap.jar:$ideaLib/extensions.jar:$ideaLib/util.jar:$ideaLib/jdom.jar:$ideaLib/log4j.jar:$ideaLib/trove4j.jar:$ideaLib/trove4j.jar"
  val jdkHome = System.getProperty("java.home")
  println(jdkHome)

  val ideaPathsSelector = "ProjectorIntelliJIdea"

  task("runIdeaServer", JavaExec::class) {
    group = "projector"
    main = "org.jetbrains.projector.server.ProjectorLauncher"
    classpath(sourceSets.main.get().runtimeClasspath, tasks.jar, ideaClassPath, "$jdkHome/../lib/tools.jar")
    jvmArgs = listOf(
      "-Dorg.jetbrains.projector.server.classToLaunch=com.intellij.idea.Main",
      "-Didea.paths.selector=$ideaPathsSelector",
      "-Didea.jre.check=true",
      "-Didea.is.internal=true",
      "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED",
      "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
      "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
      "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
      "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
      "-Djdk.attach.allowAttachSelf=true",
    ) + relayArgs
  }
}

inline fun <reified T : Task> Project.getOrCreateTask(taskName: String, noinline body: T.() -> Unit): TaskProvider<T> =
  if (tasks.names.contains(taskName)) tasks.named(taskName, T::class.java).apply { configure(body) }
  else tasks.register(taskName, T::class.java, body)

fun Project.downloadFontsInZip(
  taskName: String = "downloadFont",
  name: String,
  zipUrl: String,
  originalToDest: Map<String, String>,
): TaskProvider<Task> {
  return getOrCreateTask(taskName) {
    doFirst {
      val fontsPath = "src/main/resources/fonts"
      val requiredFonts = originalToDest.values.stream().map { "$fontsPath/$it" }.toList()

      println("Checking $name fonts: $requiredFonts")

      if (requiredFonts.stream().allMatch { project.file(it).exists() }) {
        println("$name fonts already exist, skipping download.")
      }
      else {
        println("Some $name fonts are missing, downloading... If some fonts exist, they will be overwritten.")
        project.file(fontsPath).mkdirs()

        val tempFile = File.createTempFile("${name}-fonts", "zip")
        URL(zipUrl).openStream().copyTo(tempFile.outputStream())

        originalToDest.forEach { (srcPath, dest) ->
          val destFile = project.file("$fontsPath/$dest")

          destFile.delete()
          destFile.createNewFile()

          ZipFile(tempFile).let {
            it.getInputStream(it.getEntry(srcPath))
          }
        }

        tempFile.delete()

        println("Download complete")
      }
    }
  }
}


val downloadCjkFonts = downloadFontsInZip(
  "downloadCjkFonts",
  "CJK",
  "https://noto-website-2.storage.googleapis.com/pkgs/NotoSansCJKjp-hinted.zip",
  mapOf("NotoSansCJKjp-Regular.otf" to "CJK-R.otf")
)

val downloadDefaultFonts = downloadFontsInZip(
  "downloadDefaultFonts",
  "default",
  "https://noto-website-2.storage.googleapis.com/pkgs/NotoSans-hinted.zip",
  mapOf(
    "NotoSans-Regular.ttf" to "Default-R.ttf",
    "NotoSans-Italic.ttf" to "Default-RI.ttf",
    "NotoSans-Bold.ttf" to "Default-B.ttf",
    "NotoSans-BoldItalic.ttf" to "Default-BI.ttf",
  )
)

val downloadMonoFonts = downloadFontsInZip(
  "downloadMonoFonts",
  "mono",
  "https://download.jetbrains.com/fonts/JetBrainsMono-1.0.3.zip",
  mapOf(
    "JetBrainsMono-1.0.3/ttf/JetBrainsMono-Regular.ttf" to "Mono-R.ttf",
    "JetBrainsMono-1.0.3/ttf/JetBrainsMono-Italic.ttf" to "Mono-RI.ttf",
    "JetBrainsMono-1.0.3/ttf/JetBrainsMono-Bold.ttf" to "Mono-B.ttf",
    "JetBrainsMono-1.0.3/ttf/JetBrainsMono-Bold-Italic.ttf" to "Mono-BI.ttf",
  )
)


val downloadFonts = task("downloadFonts") {
  dependsOn(downloadCjkFonts, downloadDefaultFonts, downloadMonoFonts)
}

// todo: Modify existing task which puts resources to the target dir
tasks.processResources {
  dependsOn(downloadFonts)
}
