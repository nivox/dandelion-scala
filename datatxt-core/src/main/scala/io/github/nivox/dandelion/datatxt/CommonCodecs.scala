package io.github.nivox.dandelion.datatxt

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import argonaut.{CursorHistory, DecodeResult, HCursor}

import scalaz.\/


object CommonCodecs {

  val timestampDateFormat = {
    val df = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss")
    df.setTimeZone(TimeZone.getTimeZone("GMT+0"))
    df
  }

  val timestampWithMillisDateFormat = {
    val df = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss.SSS")
    df.setTimeZone(TimeZone.getTimeZone("GMT+0"))
    df
  }

  def getTimestamp(c: HCursor): DecodeResult[Date] = {
    for {
      timestampStr <- c.get[String]("timestamp") ||| DecodeResult.fail("Missing or invalid timestamp", c.history)
      maybeTimestamp = \/.fromTryCatchNonFatal(timestampDateFormat.parse(timestampStr)) |||
        \/.fromTryCatchNonFatal(timestampWithMillisDateFormat.parse(timestampStr))
      timestamp <- maybeTimestamp.fold(
        _ => DecodeResult.fail(s"Invalid timestamp format: ${timestampStr}", new CursorHistory(List())),
        date => DecodeResult.ok(date)
      )
    } yield timestamp
  }
}
