package io.github.nivox.dandelion.datatxt.nex

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.github.nivox.dandelion.core._
import io.github.nivox.dandelion.datatxt.nex.ResponseModelsCodec._
import io.github.nivox.dandelion.datatxt.{DandelionLang, DandelionSource}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.Scalaz._
import scalaz._

object NexAPI {
  val servicePath = Uri.Path("/datatxt/nex/v1")

  def extractEntitiesStream[T](credentials: DandelionAuthCredentials,
                               lang: Option[DandelionLang] = None,
                               minConfidence: Option[Float] = None,
                               minLength: Option[Int] = None,
                               socialHashtag: Option[Boolean] = None,
                               socialMention: Option[Boolean] = None,
                               extraInfo: Set[ExtraInfo] = Set(),
                               extraTypes: Set[ExtraTypes] = Set(),
                               country: Option[String] = None,
                               customSpots: Option[String] = None,
                               epsilon: Option[Float] = None
                           )(implicit dandelionAPI: DandelionAPI, ec: ExecutionContext):
  Flow[(DandelionSource, T), (Future[EndpointError \/ EndpointResult[NexResponse]], T), NotUsed] = {
    val apiCallStream = dandelionAPI.typedApiCallStream[NexResponse, T](credentials, servicePath, err => new DandelionAPIContentException(s"Invalid DataTXT-NEX response: ${err}"))

    Flow[(DandelionSource, T)].map { case (source, k) =>
      val paramsIt: Iterator[(String, String)] =
        Iterator.single(DandelionSource.name(source) -> DandelionSource.value(source)) ++
          lang.toIterator.map("lang" -> _.lang) ++
          minConfidence.toIterator.map(c => "min_confidence" -> s"${c%1.2f}") ++
          minLength.toIterator.map("min_length" -> _.toString) ++
          socialHashtag.toIterator.map("social.hashtag" -> _.toString) ++
          socialMention.toIterator.map("social.mention" -> _.toString) ++
          (if (extraInfo.size > 0) Iterator.single("include" -> extraInfo.mkString(",")) else Iterator.empty) ++
          (if (extraTypes.size > 0) Iterator.single("extra_types" -> extraTypes.mkString(",")) else Iterator.empty) ++
          country.toIterator.map("country" -> _) ++
          customSpots.toIterator.map("custom_spots" -> _) ++
          epsilon.toIterator.map(e => "epsilon" -> s"${e%1.2f}")
      
      (FormData(paramsIt.toMap), k)
    }.via(apiCallStream)
  }


  def extractEntities(credentials: DandelionAuthCredentials,
                      source: DandelionSource,
                      lang: Option[DandelionLang] = None,
                      minConfidence: Option[Float] = None,
                      minLength: Option[Int] = None,
                      socialHashtag: Option[Boolean] = None,
                      socialMention: Option[Boolean] = None,
                      extraInfo: Set[ExtraInfo] = Set(),
                      extraTypes: Set[ExtraTypes] = Set(),
                      country: Option[String] = None,
                      customSpots: Option[String] = None,
                      epsilon: Option[Float] = None
                     )(implicit dandelionAPI: DandelionAPI, mat: Materializer, ec: ExecutionContext):
  Future[EndpointError \/ EndpointResult[NexResponse]] =
  {
    val nexStream = extractEntitiesStream[Unit](credentials, lang, minConfidence, minLength, socialHashtag, socialMention, extraInfo, extraTypes, country, customSpots, epsilon)
    val streamResF = Source.single( (source, ()) ).via(nexStream).runWith(Sink.head)

    streamResF.flatMap { case (resF, _) => resF }
  }
}