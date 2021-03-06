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

package com.here.ort.scanner.scanners

import ch.frankel.slf4k.*

import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.ScanException
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log

import java.io.File

object Licensee : LocalScanner() {
    override val scannerExe = if (OS.isWindows) "licensee.bat" else "licensee"
    override val resultFileExt = "json"

    override fun bootstrap(): File? {
        val gem = if (OS.isWindows) "gem.cmd" else "gem"
        ProcessCapture(gem, "install", "--user-install", "licensee", "-v", "9.9.0.beta.3").requireSuccess()
        val ruby = ProcessCapture("ruby", "-rubygems", "-e", "puts Gem.user_dir").requireSuccess()
        val userDir = ruby.stdout().trimEnd()
        return File(userDir, "bin")
    }

    override fun getVersion(executable: String) = getCommandVersion(scannerPath.absolutePath, "version")

    override fun scanPath(path: File, resultsFile: File): Result {
        // Licensee has issues with absolute Windows paths passed as an argument. Work around that by using the path to
        // scan as the working directory.
        val (parentPath, relativePath) = if (path.isDirectory) {
            Pair(path, ".")
        } else {
            Pair(path.parentFile, path.name)
        }

        val process = ProcessCapture(
                parentPath,
                scannerPath.absolutePath,
                "detect",
                "--json",
                relativePath
        )

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        with(process) {
            if (isSuccess()) {
                stdoutFile.copyTo(resultsFile)
                return getResult(resultsFile)
            } else {
                throw ScanException(failMessage)
            }
        }
    }

    override fun getResult(resultsFile: File): Result {
        var fileCount = 0
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()

        if (resultsFile.isFile && resultsFile.length() > 0) {
            val scanOutput = jsonMapper.readTree(resultsFile.readText())

            val licenseSummary = scanOutput["licenses"]
            val matchedFiles = scanOutput["matched_files"]

            fileCount = matchedFiles.count()

            matchedFiles.forEach {
                val licenseKey = it["matched_license"].asText()
                licenseSummary.find {
                    it["key"].asText() == licenseKey
                }?.let {
                    licenses.add(it["spdx_id"].asText())
                }
            }
        }

        return Result(fileCount, licenses, errors)
    }
}
