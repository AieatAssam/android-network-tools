/*
 * Copyright (C) 2026 The NetSwissKnife Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3

import java.io.IOException

/** The response-header budget that rejected a response. */
enum class ResponseHeaderLimitKind {
  FIELD_COUNT,
  AGGREGATE_BYTES,
  VALUE_BYTES,
  PSEUDO_FIELD_COUNT,
}

/** Thrown while parsing response metadata that exceeds a configured parser budget. */
class ResponseHeaderLimitException(
  /** The budget that was exceeded. */
  val kind: ResponseHeaderLimitKind,
  /** The maximum accepted count or byte size for [kind]. */
  val maximum: Int,
) : IOException("response header $kind limit of $maximum exceeded") {
  companion object {
    const val MAX_FIELD_OCCURRENCES = 100
    const val MAX_AGGREGATE_METADATA_BYTES = 65_536
    const val MAX_VALUE_UTF8_BYTES = 16_384
    const val FIELD_FRAMING_BYTES = 4
  }
}
