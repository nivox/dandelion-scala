package io.github.nivox.dandelion.datatxt.nex

import akka.http.scaladsl.model._
import io.github.nivox.dandelion.core._
import io.github.nivox.dandelion.datatxt.nex.ResponseModelsCodec._

import scala.concurrent.{ExecutionContext, Future}
import scalaz.Scalaz._
import scalaz._

object NexAPI {
  val servicePath = Uri.Path("/datatxt/nex/v1")

  def extractEntities(credentials: AuthCredentials,
                      source: Source,
                      lang: Option[Lang] = None,
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
  Future[EndpointError \/ EndpointResult[NexResponse]] =
  {
    val paramsIt: Iterator[(String, String)] =
      Iterator.single(Source.name(source) -> Source.value(source)) ++
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


    val params = FormData(paramsIt.toMap)
    dandelionAPI.apiCall(credentials, servicePath, params) flatMap {
      case \/-(EndpointResult(unitsInfo, rawData)) =>
        val maybeNexResp = rawData.as[NexResponse].result leftMap(_._1)
        maybeNexResp.bimap(
          err => Future.failed(new DandelionAPIContentException(s"Invalid DataTXT-NEX response: ${err}")),
          nexResp => Future.successful(EndpointResult(unitsInfo, nexResp).right)
        ) fold(identity, identity)
      case -\/(err) => Future.successful(err.left)
    }
  }
}