/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.asTextOrEmpty
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log

import java.io.File
import java.io.IOException

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.SortedSet

class Bower : PackageManager() {
    companion object : PackageManagerFactory<Bower>(
            "https://bower.io/",
            "JavaScript",
            listOf("bower.json")
    ) {
        override fun create() = Bower()
    }

    override fun command(workingDir: File) = if (OS.isWindows) {
        "bower.cmd"
    } else {
        "bower"
    }

    override fun resolveDependencies(projectDir: File, workingDir: File, definitionFile: File): AnalyzerResult? {
        val bowerComponents = File(workingDir, "bower_components")
        var tempBowerComponentsDir: File? = null

        try {
            if (bowerComponents.isDirectory) {
                val tempDir = createTempDir("analyzer", ".tmp", workingDir)
                tempBowerComponentsDir = File(tempDir, "bower_components")
                log.warn { "'$bowerComponents' already exists, temporarily moving it to '$tempBowerComponentsDir'." }
                Files.move(bowerComponents.toPath(), tempBowerComponentsDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            val scopes = sortedSetOf<Scope>()
            val packages = sortedSetOf<Package>()
            val errors = mutableListOf<String>()
            val vcsDir = VersionControlSystem.forDirectory(projectDir)

            installDependencies(workingDir)

            try {
                val pkgTree = jsonMapper.readTree(ProcessCapture(workingDir, command(workingDir), "list", "--json")
                        .requireSuccess().stdout())
                val projectPkg = parsePackage(pkgTree["pkgMeta"])

                parseScope("dependencies", pkgTree, errors, packages)?.let { scopes.add(it) }
                parseScope("devDependencies", pkgTree, errors, packages)?.let { scopes.add(it) }

                val project = Project(
                        id = projectPkg.id,
                        declaredLicenses = projectPkg.declaredLicenses,
                        aliases = emptyList(),
                        vcs = vcsDir?.getInfo(projectDir) ?: projectPkg.vcs,
                        homepageUrl = projectPkg.homepageUrl,
                        scopes = scopes)
                return AnalyzerResult(true, project, packages, errors)
            } catch (e: Exception) {
                if (com.here.ort.utils.printStackTrace) {
                    e.printStackTrace()
                }

                log.error { "Could not analyze '${definitionFile.absolutePath}': ${e.message}" }
                return null
            }
        } finally {
            // Delete bower_components folder
            if (!bowerComponents.deleteRecursively()) {
                throw IOException("Unable to delete the '$bowerComponents' directory.")
            }

            // Restore any previously existing "bower_components" directory.
            if (tempBowerComponentsDir != null) {
                log.info { "Restoring original '$bowerComponents' directory from '$tempBowerComponentsDir'." }
                Files.move(tempBowerComponentsDir.toPath(), bowerComponents.toPath(), StandardCopyOption.ATOMIC_MOVE)
                if (!tempBowerComponentsDir.parentFile.delete()) {
                    throw IOException("Unable to delete the '${tempBowerComponentsDir.parent}' directory.")
                }
            }
        }
    }

    private fun parseScope(scopeName: String, rootNode: JsonNode, errors: MutableList<String>,
            packages: SortedSet<Package>): Scope? =
            rootNode["pkgMeta"][scopeName]?.let { scopeDependenciesNode ->
                val rootDependenciesDetailsNode = rootNode["dependencies"]
                val scopeDependencies = parseDependencies(scopeDependenciesNode, rootDependenciesDetailsNode, packages,
                        errors)
                Scope(scopeName, true, scopeDependencies)
            }

    private fun parseDependencies(dependenciesToParseNode: JsonNode, detailsNode: JsonNode?, packages:
    SortedSet<Package>, errors: MutableList<String>): SortedSet<PackageReference> {
        val dependencyKeys = dependenciesToParseNode.fields().asSequence().map { it.key }
        val parsedDependencies = sortedSetOf<PackageReference>()
        dependencyKeys.forEach { dependencyName ->
            val dependencyDependencies = sortedSetOf<PackageReference>()
            val pkg = parseDependency(detailsNode, dependencyName, dependencyDependencies, packages, errors)
            pkg?.let {
                packages.add(it)
                parsedDependencies.add(it.toReference(dependencyDependencies))
            }
        }

        return parsedDependencies
    }

    private fun parseDependency(detailsNode: JsonNode?, dependencyName: String,
            dependencyDependencies: SortedSet<PackageReference>, packages: SortedSet<Package>,
            errors: MutableList<String>): Package? =
            detailsNode?.let {
                return try {
                    val pkgDetailsNode = it[dependencyName]
                    val namespace = pkgDetailsNode["endpoint"]["source"].asText().substringBefore("/")
                    val pkgMetaNode = pkgDetailsNode["pkgMeta"]
                    val pkgDependenciesNode = pkgMetaNode["dependencies"]
                    pkgDependenciesNode?.let {
                        dependencyDependencies.addAll(
                                parseDependencies(it, pkgDetailsNode["dependencies"], packages, errors)
                        )
                    }
                    parsePackage(pkgMetaNode, namespace)
                } catch (e: Exception) {
                    if (com.here.ort.utils.printStackTrace) {
                        e.printStackTrace()
                    }

                    val errorMsg = "Failed to parse package $dependencyName: ${e.message}"
                    log.error { errorMsg }
                    errors.add(errorMsg)
                    null
                }
            }

    private fun parsePackage(pkgMetaNode: JsonNode, namespace: String = ""): Package {
        val pkgName = pkgMetaNode["name"].asTextOrEmpty()
        val version = pkgMetaNode["version"].asTextOrEmpty()
        val license = pkgMetaNode["license"].asTextOrEmpty()
        val description = pkgMetaNode["description"].asTextOrEmpty()
        val repoNode = pkgMetaNode.get("repository")
        val vcs = if (repoNode == null) {
            val url = pkgMetaNode["_source"].asTextOrEmpty()
            if (url.isNotBlank()) {
                val vcs = VersionControlSystem.splitUrl(url)
                if (pkgMetaNode["_resolution"] != null) {
                    val commit = pkgMetaNode["_resolution"]["commit"].asTextOrEmpty()
                    vcs.copy(revision = commit)
                } else {
                    vcs
                }
            } else {
                VcsInfo.EMPTY
            }
        } else {
            VcsInfo(repoNode["type"].asTextOrEmpty(), repoNode["url"].asTextOrEmpty(), "", "")
        }
        val homepage = pkgMetaNode["homepage"].asTextOrEmpty()

        return Package(
                id = Identifier(
                        packageManager = javaClass.simpleName,
                        namespace = namespace,
                        name = pkgName,
                        version = version
                ),
                declaredLicenses = sortedSetOf(license),
                description = description,
                homepageUrl = homepage,
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcs,
                vcsProcessed = vcs.normalize()
        )
    }

    private fun installDependencies(workingDir: File) {
        ProcessCapture(workingDir, command(workingDir), "install").requireSuccess()
    }
}
