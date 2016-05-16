package io.github.nivox.dandelion.core

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import argonaut.Argonaut.jEmptyObject
import argonaut.{DecodeJson, DecodeResult, Json}
import io.github.nivox.akka.http.argonaut.ArgonautSupport._
import io.github.nivox.dandelion.core.DandelionHeaders._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz.Scalaz._
import scalaz._

class DandelionAPIException(message: String, cause: Throwable = null) extends Exception(message, cause)
class DandelionAPIUnitsInfoException(message: String) extends DandelionAPIException(message)

class DandelionAPIContentException(message: String) extends DandelionAPIException(message)
class DandelionAPIDataException(message: String) extends DandelionAPIContentException(message)
class DandelionAPIErrorException(message: String) extends DandelionAPIContentException(message)


object DandelionAPI {
  def apply(authority: Uri.Authority, log: LoggingAdapter)(implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): DandelionAPI =
    new DandelionAPI(authority, log)

  def apply(authority: Uri.Authority = Uri.Authority(Uri.Host("api.dandelion.eu"), 443))(implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): DandelionAPI = {
    val log = Logging.getLogger(actorSystem, classOf[DandelionAPI].getCanonicalName)
    new DandelionAPI(authority, log)
  }
}

class DandelionAPI(authority: Uri.Authority, log: LoggingAdapter)(implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext) {
  private def extractUnitsInfo(resp: HttpResponse): String \/ UnitsInfo =
    for {
      units <- resp.header[DandelionUnits] toRightDisjunction s"Missing or invalid ${DandelionUnits.name} header"
      uniapiCost <- resp.header[DandelionUniApiCost] toRightDisjunction s"Missing or invalid ${DandelionUniApiCost.name} header"
      left <- resp.header[DandelionUnitsLeft] toRightDisjunction s"Missing or invalid ${DandelionUnitsLeft.name} header"
      reqId <- resp.header[DandelionRequestId] toRightDisjunction s"Missing or invalid ${DandelionRequestId.name} header"
    } yield UnitsInfo(units.units, left.unitsLeft, uniapiCost.uniapiCost, reqId.requestId)

  private def extractData(resp: HttpResponse): Future[String \/ Json] =
    Unmarshal(resp.entity).to[Json].map(_.right).recover { case err => err.getMessage.left }

  private def extractError(resp: HttpResponse): Future[String \/ EndpointError] = {
    val maybeJsonRespF = Unmarshal(resp.entity).to[Json].map(_.right).recover { case err => err.getMessage.left }

    maybeJsonRespF.map { maybeJsonResp =>
      for {
        jsonResp <- maybeJsonResp
        c = jsonResp.hcursor
        message <- (c --\ "message").as[String].result leftMap (_ => "No error message specified")
        code <- (c --\ "code").as[String].result map (DandelionError.fromCode) leftMap (_ => "No error code specified")
        data <- ((c --\ "data").as[Json] ||| DecodeResult.ok(jEmptyObject)).result leftMap (t => s"Invalid data: ${t._1}")
      } yield EndpointError(message, code, data)
    }
  }


  private def handleSuccessfulResponse(_resp: HttpResponse): Future[EndpointError \/ EndpointResult[Json]] = {
    val resp = _resp.mapHeaders(decodeDandelionHeaders)
    val maybeUnitsInfo = extractUnitsInfo(resp)
    val maybeDataFuture = extractData(resp)

    maybeDataFuture.flatMap { maybeData =>
      val result = for {
        data <- maybeData leftMap (new DandelionAPIDataException(_))
      } yield EndpointResult(maybeUnitsInfo, data)

      result.fold(
        (err) => Future.failed(err),
        (succ) => Future.successful(succ.right)
      )
    }
  }

  private def handleErrorResponse(resp: HttpResponse): Future[EndpointError \/ EndpointResult[Json]] = {
    val maybeErrorFuture = extractError(resp)

    maybeErrorFuture.flatMap { maybeError =>
      maybeError.fold(
        (err) => Future.failed(new DandelionAPIErrorException(err)),
        (succ) => Future.successful(succ.left)
      )
    }
  }

  def requestEntity(credentials: DandelionAuthCredentials, params: FormData): RequestEntity = {
    val paramsWithAuth = params.copy(
      fields = ("$app_id", credentials.appId) +:
        ("$app_key", credentials.appKey) +:
        params.fields
    )

    paramsWithAuth.toEntity
  }


  def apiCallStream[U](credentials: DandelionAuthCredentials, servicePath: Uri.Path): Flow[(FormData, U), (Future[EndpointError \/ EndpointResult[Json]], U), NotUsed] = {
    val connPool = Http().cachedHostConnectionPoolHttps[U](authority.host.address, authority.port)

    val buildRequest = Flow.fromFunction[(FormData, U), (HttpRequest, U)] { case (params, data) =>
      val req = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri().withPath(servicePath),
        entity = requestEntity(credentials, params)
      )

      log.debug(s"Request to ${authority.host}:${authority.port} userData=[${data}]: ${req}")
      req -> data
    }

    val parseResponse = Flow.fromFunction[(Try[HttpResponse], U), (Future[EndpointError \/ EndpointResult[Json]], U)] { case (respTry, data) =>
      log.debug(s"Response from ${authority.host}:${authority.port} userData=[${data}]: ${respTry}")
      val out = respTry match {
        case Success(resp) if resp.status == StatusCodes.OK => handleSuccessfulResponse(resp)
        case Success(resp) => handleErrorResponse(resp)
        case Failure(err) => Future.failed(err)
      }

      out -> data
    }

    buildRequest via connPool via parseResponse
  }


  def typedApiCallStream[T, U](credentials: DandelionAuthCredentials, servicePath: Uri.Path, errF: String => Throwable)(implicit respDecode: DecodeJson[T]): Flow[(FormData, U), (Future[EndpointError \/ EndpointResult[T]], U), NotUsed] = {
    apiCallStream(credentials, servicePath).map { case (resF, u) =>
      val typedResF = resF flatMap {
        case \/-(EndpointResult(unitsInfo, rawData)) =>
          val maybeTypedResp = rawData.as[T].result leftMap (_._1)
          maybeTypedResp.bimap(
            err => Future.failed(errF(err)),
            typedResp => Future.successful(EndpointResult(unitsInfo, typedResp).right)
          ) fold(identity, identity)
        case -\/(err) => Future.successful(err.left)
      }

      typedResF -> u
    }
  }

  def apiCall(credentials: DandelionAuthCredentials, servicePath: Uri.Path, params: FormData): Future[EndpointError \/ EndpointResult[Json]] = {
    val dandelionFlow = apiCallStream[Unit](credentials, servicePath)
    val res = Source.single( (params, ()) ).via(dandelionFlow).runWith(Sink.head)

    for {
      (respF, _) <- res
      resp <- respF
    } yield resp
  }


  def typedApiCall[T](credentials: DandelionAuthCredentials, servicePath: Uri.Path, params: FormData, errF: String => Throwable)(implicit respDecode: DecodeJson[T]): Future[EndpointError \/ EndpointResult[T]] = {
    val dandelionFlow = typedApiCallStream[T, Unit](credentials, servicePath, errF)
    val res = Source.single( (params, ()) ).via(dandelionFlow).runWith(Sink.head)

    for {
      (respF, _) <- res
      resp <- respF
    } yield resp
  }
}
