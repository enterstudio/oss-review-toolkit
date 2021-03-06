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

package com.here.ort.utils.spdx

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils

import java.io.File

/**
 * Calculate the [SPDX package verification code][1] for a list of known SHA1s of files.
 *
 * [1]: https://spdx.github.io/spdx-spec/chapters/3-package-information.html#39-package-verification-code-
 */
@JvmName("calculatePackageVerificationCodeForStrings")
fun calculatePackageVerificationCode(sha1sums: List<String>): String =
        Hex.encodeHexString(sha1sums.sorted().fold(DigestUtils.getSha1Digest()) { digest, sha1sum ->
            DigestUtils.updateDigest(digest, sha1sum)
        }.digest())

/**
 * Calculate the [SPDX package verification code][1] for a list of files.
 *
 * [1]: https://spdx.github.io/spdx-spec/chapters/3-package-information.html#39-package-verification-code-
 */
@JvmName("calculatePackageVerificationCodeForFiles")
fun calculatePackageVerificationCode(files: List<File>) =
        calculatePackageVerificationCode(files.map { file ->
            file.inputStream().use { DigestUtils.sha1Hex(it) }
        })
