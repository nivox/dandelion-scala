package io.github.nivox.dandelion.datatxt.sent

import akka.http.scaladsl.model.{FormData, Uri}
import io.github.nivox.dandelion.core._
import io.github.nivox.dandelion.datatxt.sent.ResponseModelsCodec._
import io.github.nivox.dandelion.datatxt.{Lang, Source}

import scala.concurrent.{ExecutionContext, Future}
import scalaz._

object SentAPI {

  val servicePath = Uri.Path("/datatxt/sent/v1")

  def sentiment(credentials: AuthCredentials,
                source: Source,
                lang: Option[Lang] = None
               )(implicit dandelionAPI: DandelionAPI, ex: ExecutionContext):
  Future[EndpointError \/ EndpointResult[SentResponse]] =
  {
    val paramsIt: Iterator[(String, String)] =
      Iterator.single(Source.name(source) -> Source.value(source)) ++
        lang.toIterator.map("lang" -> _.lang)

    val params = FormData(paramsIt.toMap)
    dandelionAPI.typedApiCall[SentResponse](
      credentials,
      servicePath,
      params,
      err => new DandelionAPIContentException(s"Invalid DataTXT-SENT response: ${err}")
    )
  }
}
