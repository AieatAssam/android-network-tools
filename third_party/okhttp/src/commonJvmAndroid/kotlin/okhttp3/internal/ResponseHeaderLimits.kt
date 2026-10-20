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
package okhttp3.internal

import okhttp3.ResponseHeaderLimitException
import okhttp3.ResponseHeaderLimitKind

internal const val RESPONSE_HEADER_FIELD_LIMIT = ResponseHeaderLimitException.MAX_FIELD_OCCURRENCES
internal const val RESPONSE_HEADER_AGGREGATE_BYTE_LIMIT = ResponseHeaderLimitException.MAX_AGGREGATE_METADATA_BYTES
internal const val RESPONSE_HEADER_VALUE_BYTE_LIMIT = ResponseHeaderLimitException.MAX_VALUE_UTF8_BYTES
internal const val RESPONSE_HEADER_FIELD_OVERHEAD_BYTES = ResponseHeaderLimitException.FIELD_FRAMING_BYTES

internal fun responseHeaderValueLimitExceeded(): ResponseHeaderLimitException =
  ResponseHeaderLimitException(ResponseHeaderLimitKind.VALUE_BYTES, RESPONSE_HEADER_VALUE_BYTE_LIMIT)

internal fun responseHeaderAggregateLimitExceeded(): ResponseHeaderLimitException =
  ResponseHeaderLimitException(ResponseHeaderLimitKind.AGGREGATE_BYTES, RESPONSE_HEADER_AGGREGATE_BYTE_LIMIT)
