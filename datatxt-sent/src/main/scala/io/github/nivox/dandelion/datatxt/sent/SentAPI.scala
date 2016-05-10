package io.github.nivox.dandelion.datatxt.sent

import akka.NotUsed
import akka.http.scaladsl.model.{FormData, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.github.nivox.dandelion.core._
import io.github.nivox.dandelion.datatxt.sent.ResponseModelsCodec._
import io.github.nivox.dandelion.datatxt.{DandelionLang, DandelionSource}

import scala.concurrent.{ExecutionContext, Future}
import scalaz._

object SentAPI {

  val servicePath = Uri.Path("/datatxt/sent/v1")

  def sentimentStream[T](credentials: DandelionAuthCredentials,
                      lang: Option[DandelionLang] = None
                     )(implicit dandelionAPI: DandelionAPI, ex: ExecutionContext):
  Flow[(DandelionSource, T), (Future[EndpointError \/ EndpointResult[SentResponse]], T), NotUsed] = {
    val apiCallStream = dandelionAPI.typedApiCallStream[SentResponse, T](credentials, servicePath, err => new DandelionAPIContentException(s"Invalid DataTXT-SENT response: ${err}"))

    Flow[(DandelionSource, T)].map { case (source, k) =>
      val paramsIt: Iterator[(String, String)] =
        Iterator.single(DandelionSource.name(source) -> DandelionSource.value(source)) ++
          lang.toIterator.map("lang" -> _.lang)

      (FormData(paramsIt.toMap), k)
    }.via(apiCallStream)
  }

  def sentiment(credentials: DandelionAuthCredentials,
                source: DandelionSource,
                lang: Option[DandelionLang] = None
               )(implicit dandelionAPI: DandelionAPI, mat: Materializer, ex: ExecutionContext):
  Future[EndpointError \/ EndpointResult[SentResponse]] =
  {
    val sentStream = sentimentStream[Unit](credentials, lang)
    val streamResF = Source.single( (source, ()) ).via(sentStream).runWith(Sink.head)
    streamResF.flatMap { case (resF, _) => resF }
  }
}
