package io.github.nivox.dandelion.datatxt.nex

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import akka.http.scaladsl.model.Uri
import io.github.nivox.dandelion.datatxt.CommonCodecs

case class WikipediaResource(id: Int, title: String, uri: Uri)

case class Spot(word: String, start: Int, end: Int)

case class Lod(wikipedia: Uri, dbpedia: Uri)

case class Image(full: Uri, thumbnail: Uri)

case class Annotation(resource: WikipediaResource,
                      label: String,
                      confidence: Float,
                      spot: Spot,
                      types: List[Uri],
                      categories: List[String],
                      abstractS: Option[String],
                      lod: Option[Lod],
                      alternateLabels: List[String],
                      image: Option[Image]
                     )

case class NexResponse(timestamp: Date,
                       time: Int,
                       lang: String,
                       langConfidence: Option[Float],
                       text: Option[String],
                       url: Option[Uri],
                       annotations: List[Annotation]
                      )

object ResponseModelsCodec {
  import argonaut._
  import Argonaut._

  import scalaz._
  import Scalaz._

  def getOrElse[T](cursor: HCursor, field: String, default: => Option[T])(implicit d: DecodeJson[T]): DecodeResult[Option[T]] =
    (cursor --\ field).focus map (_.as[T] map (_.some)) getOrElse DecodeResult.ok(default)

  implicit val wikipediaResourceDecode: DecodeJson[WikipediaResource] = DecodeJson { c =>
    for {
      id <- c.get[Int]("id") ||| DecodeResult.fail("Missing or invalid wikipedia resource id", c.history)
      title <- c.get[String]("title") ||| DecodeResult.fail("Missing or invalid wikipedia resource title", c.history)
      uri <- c.get[String]("uri") ||| DecodeResult.fail("Missing or invalid wikipedia resource uri", c.history)
    } yield WikipediaResource(id, title, uri)
  }

  implicit val spotDecode: DecodeJson[Spot] = DecodeJson { c =>
    for {
      word <- c.get[String]("spot") ||| DecodeResult.fail("Missing or invalid spot", c.history)
      start <- c.get[Int]("start") ||| DecodeResult.fail("Missing or invalid start", c.history)
      end <- c.get[Int]("end") ||| DecodeResult.fail("Missing or invalid end", c.history)
    } yield Spot(word, start, end)
  }

  implicit val lodDecode: DecodeJson[Lod] = DecodeJson { c =>
    for {
      wikipedia <- c.get[String]("wikipedia") ||| DecodeResult.fail("Missing or invalid wikipedia lod", c.history)
      dbpedia <- c.get[String]("dbpedia") ||| DecodeResult.fail("Missing or invalid dbpedia lod", c.history)
    } yield Lod(wikipedia, dbpedia)
  }

  implicit val imageDecode: DecodeJson[Image] = DecodeJson { c =>
    for {
      full <- c.get[String]("full") ||| DecodeResult.fail("Missing or invalid full image url", c.history)
      thumbnail <- c.get[String]("thumbnail") ||| DecodeResult.fail("Missing or invalid thumbnail image url", c.history)
    } yield Image(full, thumbnail)
  }

  implicit val annotationDecode: DecodeJson[Annotation] = DecodeJson { c =>
    for {
      resource <- c.focus.as[WikipediaResource]
      label <- c.get[String]("label") ||| DecodeResult.fail("Missing or invalid label", c.history)
      confidence <- c.get[Float]("confidence") ||| DecodeResult.fail("Missing or invalid confidence", c.history)
      spot <- c.focus.as[Spot]
      typeStrLst <- getOrElse[List[String]](c, "types", None).map(_.getOrElse(List())) ||| DecodeResult.fail("Missing or invalid types", c.history)
      typeLst = typeStrLst.map(Uri(_))
      categories <- getOrElse[List[String]](c, "categories", None).map(_.getOrElse(List())) ||| DecodeResult.fail("Missing or invalid categories", c.history)
      lod <- getOrElse[Lod](c, "lod", None)
      abstractS <- getOrElse[String](c, "abstract", None) ||| DecodeResult.fail("Invalid abstract", c.history)
      alternateLabels <- getOrElse[List[String]](c, "alternateLabels", None).map(_.getOrElse(List())) ||| DecodeResult.fail("Invalid alternate labels", c.history)
      image <- getOrElse[Image](c, "image", None)
    } yield Annotation(resource, label, confidence, spot, typeLst, categories, abstractS, lod, alternateLabels, image)
  }

  val timestampDateFormat = {
    val df = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss")
    df.setTimeZone(TimeZone.getTimeZone("GMT+0"))
    df

  }
  implicit val nexResponseDecode: DecodeJson[NexResponse] = DecodeJson { c =>
    for {
      timestamp <- CommonCodecs.getTimestamp(c)
      time <- c.get[Int]("time") ||| DecodeResult.fail("Missing or invalid time", c.history)
      lang <- c.get[String]("lang") ||| DecodeResult.fail("Missing or invalid lang", c.history)
      langConfidence <- getOrElse[Float](c, "langConfidence", None) ||| DecodeResult.fail("Invalid language confidence", c.history)
      text <- getOrElse[String](c, "text", None) ||| DecodeResult.fail("Invalid text", c.history)
      urlStr <- getOrElse[String](c, "url", None) ||| DecodeResult.fail("Invalid url", c.history)
      url = urlStr map (Uri(_))
      annotations <- c.get[List[Annotation]]("annotations")
    } yield NexResponse(timestamp, time, lang, langConfidence, text, url, annotations)
  }
}