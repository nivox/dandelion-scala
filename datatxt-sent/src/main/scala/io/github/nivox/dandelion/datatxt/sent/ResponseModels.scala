package io.github.nivox.dandelion.datatxt.sent

import java.util.Date

import io.github.nivox.dandelion.datatxt.CommonCodecs

trait SentimentType
object SentimentType {
  object Positive extends SentimentType {
    override def toString: String = "positive"
  }
  object Neutral extends SentimentType {
    override def toString: String = "neutral"
  }
  object Negative extends SentimentType {
    override def toString: String = "negative"
  }
}

case class Sentiment(score: Float, sentimentType: SentimentType)

case class SentResponse(timestamp: Date, time: Int, lang: String, sentiment: Sentiment)

object ResponseModelsCodec {
  import argonaut._
  import Argonaut._

  implicit val sentimentTypeDecode: DecodeJson[SentimentType] = DecodeJson { c =>
    for {
      stypeStr <- c.focus.as[String]
      stype <- stypeStr match {
        case "positive" => DecodeResult.ok(SentimentType.Positive)
        case "neutral" => DecodeResult.ok(SentimentType.Neutral)
        case "negative" => DecodeResult.ok(SentimentType.Negative)
        case _ => DecodeResult.fail(s"Unknown sentiment type [${stypeStr}]", c.history)
      }
    } yield stype
  }

  implicit val sentimentDecode: DecodeJson[Sentiment] = DecodeJson { c =>
    for {
      score <- c.get[Float]("score") ||| DecodeResult.fail("Missing or invalid sentiment score", c.history)
      stype <- c.get[SentimentType]("type") ||| DecodeResult.fail("Missing or invalid sentiment type", c.history)
    } yield Sentiment(score, stype)
  }


  implicit val sentResponseDecode: DecodeJson[SentResponse] = DecodeJson { c =>
    for {
      timestamp <- CommonCodecs.getTimestamp(c)
      time <- c.get[Int]("time") ||| DecodeResult.fail("Missing or invalid time", c.history)
      lang <- c.get[String]("lang") ||| DecodeResult.fail("Missing or invalid lang", c.history)
      sentiment <- c.get[Sentiment]("sentiment")
    } yield SentResponse(timestamp, time, lang, sentiment)
  }
}