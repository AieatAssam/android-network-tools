/*
 * Copyright (C) 2012 The Android Open Source Project
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
package okhttp3.internal.http1

import java.io.IOException
import okhttp3.Headers
import okhttp3.internal.HEADER_LIMIT
import okhttp3.internal.RESPONSE_HEADER_AGGREGATE_BYTE_LIMIT
import okhttp3.internal.RESPONSE_HEADER_FIELD_LIMIT
import okhttp3.internal.RESPONSE_HEADER_FIELD_OVERHEAD_BYTES
import okhttp3.internal.RESPONSE_HEADER_VALUE_BYTE_LIMIT
import okhttp3.internal.responseHeaderAggregateLimitExceeded
import okhttp3.internal.responseHeaderValueLimitExceeded
import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8

/**
 * Parse all headers delimited by "\r\n" until an empty line. This throws if headers exceed 256 KiB.
 */
class HeadersReader(
  val source: BufferedSource,
) {
  private var headerLimit = HEADER_LIMIT
  private var responseHeaderFieldCount = 0
  private var responseHeaderMetadataByteCount = 0

  /** Read a single line counted against the header size limit. */
  fun readLine(): String {
    val line = source.readUtf8LineStrict(headerLimit)
    headerLimit -= line.length.toLong()
    return line
  }

  /** Reads headers or trailers. */
  fun readHeaders(): Headers {
    val result = Headers.Builder()
    while (true) {
      val line = readHeaderLine()
      if (line.isEmpty()) break

      responseHeaderFieldCount++
      if (responseHeaderFieldCount > RESPONSE_HEADER_FIELD_LIMIT) {
        throw okhttp3.ResponseHeaderLimitException(
          okhttp3.ResponseHeaderLimitKind.FIELD_COUNT,
          RESPONSE_HEADER_FIELD_LIMIT,
        )
      }

      // Parse one field in isolation so its normalized name/value can be measured before the
      // response Headers.Builder retains it. The line reader itself is bounded to the aggregate
      // policy plus HTTP/1's colon and optional-space framing.
      val parsed = Headers.Builder().addLenient(line).build()
      val name = parsed.name(0)
      val value = parsed.value(0)
      val valueBytes = value.encodeUtf8().size
      if (valueBytes > RESPONSE_HEADER_VALUE_BYTE_LIMIT) throw responseHeaderValueLimitExceeded()

      val fieldBytes =
        name.encodeUtf8().size + valueBytes + RESPONSE_HEADER_FIELD_OVERHEAD_BYTES
      if (responseHeaderMetadataByteCount + fieldBytes > RESPONSE_HEADER_AGGREGATE_BYTE_LIMIT) {
        throw responseHeaderAggregateLimitExceeded()
      }
      responseHeaderMetadataByteCount += fieldBytes
      result.addLenient(name, value)
    }
    return result.build()
  }

  /**
   * Read a header line without first allocating up to OkHttp's looser 256 KiB transport limit.
   * The decoded metadata budget is tighter; the extra two bytes cover ':' and HTTP/1 OWS framing.
   */
  private fun readHeaderLine(): String {
    val line = Buffer()
    var byteCount = 0
    var colonIndex = -1
    while (true) {
      val b = source.readByte()
      if (b == '\n'.code.toByte()) break
      if (byteCount >= RESPONSE_HEADER_AGGREGATE_BYTE_LIMIT + 2) {
        val rawValueBytes = if (colonIndex < 0) 0 else byteCount - colonIndex - 1
        if (rawValueBytes > RESPONSE_HEADER_VALUE_BYTE_LIMIT + 2) {
          throw responseHeaderValueLimitExceeded()
        }
        throw responseHeaderAggregateLimitExceeded()
      }
      if (colonIndex < 0 && byteCount > 0 && b == ':'.code.toByte()) colonIndex = byteCount
      line.writeByte(b.toInt())
      byteCount++
    }

    val wireLineBytes = byteCount.toLong() - if (byteCount > 0 && line[byteCount.toLong() - 1] == '\r'.code.toByte()) 1 else 0
    headerLimit -= wireLineBytes
    if (headerLimit < 0L) throw IOException("header size limit of $HEADER_LIMIT exceeded")

    val decoded = line.readUtf8()
    return if (decoded.endsWith('\r')) decoded.dropLast(1) else decoded
  }
}
