/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2019-2020 jsnimda <7615255+jsnimda@users.noreply.github.com>
 *   Copyright (c) 2021-2022 Plamen K. Kosseff <p.kosseff@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import masecla.modrinth4j.model.version.ProjectVersion.*
import net.darkhax.curseforgegradle.Constants
import net.darkhax.curseforgegradle.TaskPublishCurseForge


val controlify_version: String? = "2.4.1+1.21.1-neoforge"
val yacl_version: String? = "3.7.1+1.21.1-neoforge"


plugins {
    alias(libs.plugins.easymod)
    antlr
    id("org.barfuin.gradle.taskinfo") version "2+"
}


dependencies {
    shadedApi("com.yevdo:jwildcard:1.4")
    val antlrVersion = "4.13.2"
    antlr("org.antlr:antlr4:$antlrVersion")
    shadedApi("org.antlr:antlr4-runtime:$antlrVersion") {
        version {
            strictly(antlrVersion)
        }
    }
    runtimeOnly("org.antlr:antlr4-runtime:$antlrVersion") {
        version {
            strictly(antlrVersion)
        }
    }
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:[1.7.0,2)")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:[1.7.0,2)")

    compileOnly("curse.maven:easy-villagers-400514:4584220")


    yacl_version?.let {
        compileOnly("dev.isxander:yet-another-config-lib:$it") {
            exclude("maven.modrinth")
            exclude(module = "reeses-sodium-options")
        }
    }
    controlify_version?.let {
        compileOnly("dev.isxander:controlify:$it") {
            exclude("maven.modrinth")
            exclude(module = "reeses-sodium-options")
            exclude(group = "net.fabricmc.fabric-api")
        }
    }

}

easyMod {
    this.enableShadow = true
    this.enableProGuard = true

    this.shadowJarConfig = {
        relocate("org.antlr", "org.anti_ad.embedded.org.antlr")
        relocate("com.yevdo", "org.anti_ad.embedded.com.yevdo")
        minimize()
    }
    this.jarPostProcessConfig = {
        advzipArguments = if (modVersion.release()) {
            listOf("-4", "-z", "-i", "100")
        } else {
            listOf("-3", "-z", "-i", "10")
        }
    }
}

tasks.named<AntlrTask>("generateGrammarSource").configure {
    val pkg = "org.anti_ad.mc.ipnext.gen"
    outputDirectory = file("build/generated-src/antlr/main/${pkg.replace('.', '/')}")
    arguments = listOf("-visitor", "-package", pkg,
                       "-Xexact-output-dir")
}

tasks.named("compileKotlin") {
    dependsOn("generateGrammarSource")
}

tasks.named("compileJava") {
    dependsOn("generateGrammarSource")
}
tasks.named("sourcesJar") {
    dependsOn("generateGrammarSource")
}

publishing {
    repositories {
        maven {
            val releasesRepoUrl = "https://maven.ipn-mod.org/releases"
            val snapshotsRepoUrl = "https://maven.ipn-mod.org/snapshots"
            name = "ipnOfficialRepo"
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
            url = uri(if (modVersion.release()) releasesRepoUrl else snapshotsRepoUrl)
        }
    }
}


afterEvaluate {
    modrinth {
        failSilently.set(true)
        if (System.getenv("IPNEXT_RELEASE") != null) {
            token.set(System.getenv("MODRINTH_TOKEN"))
        }
        versionType.set(VersionType.RELEASE.name)
    }
    project.tasks.withType(TaskPublishCurseForge::class.java) {
        if (System.getenv("IPNEXT_RELEASE") != null) {
            this.apiToken = System.getenv("CURSEFORGE_DEPOY_TOKEN")
        }
        this.uploadArtifacts.forEach {
            it.releaseType = Constants.RELEASE_TYPE_RELEASE
        }
    }

}
