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

val antlrVersion: String by project

plugins {
    idea
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlinSer) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.easymod)

    id("org.barfuin.gradle.taskinfo") version "2+"
    antlr
}

dependencies {
    antlr("org.antlr:antlr4:$antlrVersion") {
        version {
            strictly(antlrVersion)
        }
    }
}


configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")

}

modVersion {
    semanticVersion = "2.3.6"
    release = {
        System.getenv("IPNEXT_RELEASE") != null
    }
}


easyMod {
    collectArtifacts = true
}

project.allprojects {
    if (this.rootProject != this) {
        group = "org.anti_ad.mc"
    }

}


