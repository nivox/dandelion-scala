package io.github.nivox.dandelion.datatxt

import java.time._
import java.time.format.DateTimeFormatter
import java.util.Date

import argonaut.{CursorHistory, DecodeResult, HCursor}

import scalaz.\/


object CommonCodecs {

  val timestampDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]")

  def getTimestamp(c: HCursor): DecodeResult[Date] = {
    for {
      timestampStr <- c.get[String]("timestamp") ||| DecodeResult.fail("Missing or invalid timestamp", c.history)
      maybeTimestamp = \/.fromTryCatchNonFatal {
        LocalDateTime.parse(timestampStr, timestampDateFormatter).atZone(ZoneOffset.UTC)
      }
      timestamp <- maybeTimestamp.fold(
        err => DecodeResult.fail(s"Invalid timestamp format: [${timestampStr}]: ${err.getMessage}", new CursorHistory(List())),
        date => DecodeResult.ok(date)
      )
    } yield new Date(timestamp.toInstant.toEpochMilli)
  }
}
