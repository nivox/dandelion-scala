package io.github.nivox.dandelion.core

import java.time.{OffsetTime, ZoneOffset}

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

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
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

class DandelionAPI(authority: Uri.Authority, log: LoggingAdapter, defaultUnitResetTime: OffsetTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC))
                  (implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext) {
  private def extractUnitsInfo(resp: HttpResponse): String \/ UnitsInfo =
    for {
      units <- resp.header[DandelionUnits] toRightDisjunction s"Missing or invalid ${DandelionUnits.name} header"
      uniapiCost <- resp.header[DandelionUniApiCost] toRightDisjunction s"Missing or invalid ${DandelionUniApiCost.name} header"
      left <- resp.header[DandelionUnitsLeft] toRightDisjunction s"Missing or invalid ${DandelionUnitsLeft.name} header"
      reqId <- resp.header[DandelionRequestId] toRightDisjunction s"Missing or invalid ${DandelionRequestId.name} header"
      resetTime <- resp.header[DandelionUnitsReset] toRightDisjunction s"Missing or invalid ${DandelionUnitsReset.name} header"
    } yield UnitsInfo(units.units, left.unitsLeft, uniapiCost.uniapiCost, resetTime.resetTime, reqId.requestId)

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
    val fieldsWithAuth = credentials.toFormData.fields.foldLeft(params.fields) { case (acc, field) => field +: acc }
    FormData(fieldsWithAuth).toEntity
  }


  def apiCallStream[U](credentials: DandelionAuthCredentials, servicePath: Uri.Path, requestTimeout: Duration = Duration.Inf)
  : Flow[(FormData, U), (ApiCallError \/ EndpointResult[Json], U), NotUsed] = {
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

    val parseResponse = Flow.fromFunction[(Try[HttpResponse], U), (ApiCallError \/ EndpointResult[Json], U)] { case (respTry, data) =>
      log.debug(s"Response from ${authority.host}:${authority.port} userData=[${data}]: ${respTry}")
      val out = respTry match {
        case Success(resp) if resp.status == StatusCodes.OK => handleSuccessfulResponse(resp)
        case Success(resp) => handleErrorResponse(resp)
        case Failure(err) => Future.failed(err)
      }

      val res = \/.fromTryCatchNonFatal(Await.result(out, requestTimeout)) match {
        case \/-(endpointRes) => endpointRes
        case -\/(err) => -\/(CallException("Timeout parsing response", err))
      }

      res -> data
    }

    buildRequest via connPool via parseResponse
  }

  def apiCallStreamLimited[U](credentials: DandelionAuthCredentials, servicePath: Uri.Path, requestTimeout: Duration = Duration.Inf)
  : Flow[(FormData, U), (ApiCallError \/ EndpointResult[Json], U), NotUsed] = {
    ApiCallRateLimitFlow[Json, U](
      apiCallStream(credentials, servicePath, requestTimeout),
      log,
      defaultUnitResetTime.toLocalTime,
      defaultUnitResetTime.getOffset
    )
  }


  def typedApiCallStream[T, U](credentials: DandelionAuthCredentials, servicePath: Uri.Path, errF: String => Throwable, requestTimeout: Duration = Duration.Inf)
                              (implicit respDecode: DecodeJson[T])
  : Flow[(FormData, U), (ApiCallError \/ EndpointResult[T], U), NotUsed] = {
    apiCallStream(credentials, servicePath, requestTimeout).map { case (res, u) =>
      val typedRes = res.flatMap { endpointResult =>
        val maybeTypedResp = endpointResult.data.as[T].result leftMap (_._1)
        maybeTypedResp.bimap(
          err => ResponseError("Error decoding endpoint result data", errF(err)),
          typedResp => EndpointResult(endpointResult.unitsInfo, typedResp)
        )
      }

      typedRes -> u
    }
  }

  def typedApiCallStreamLimited[T, U](credentials: DandelionAuthCredentials, servicePath: Uri.Path, errF: String => Throwable, requestTimeout: Duration = Duration.Inf)
                                     (implicit respDecode: DecodeJson[T])
  : Flow[(FormData, U), (ApiCallError \/ EndpointResult[T], U), NotUsed] = {
    ApiCallRateLimitFlow[T, U](
      typedApiCallStream(credentials, servicePath, errF, requestTimeout),
      log,
      defaultUnitResetTime.toLocalTime,
      defaultUnitResetTime.getOffset
    )
  }

  def apiCall(credentials: DandelionAuthCredentials, servicePath: Uri.Path, params: FormData): Future[ApiCallError \/ EndpointResult[Json]] = {
    val dandelionFlow = apiCallStream[Unit](credentials, servicePath, Duration.Inf)
    val res = Source.single( (params, ()) ).via(dandelionFlow).runWith(Sink.head)

    res.map(_._1)
  }


  def typedApiCall[T](credentials: DandelionAuthCredentials, servicePath: Uri.Path, params: FormData, errF: String => Throwable)
                     (implicit respDecode: DecodeJson[T])
  : Future[ApiCallError \/ EndpointResult[T]] = {
    val dandelionFlow = typedApiCallStream[T, Unit](credentials, servicePath, errF, Duration.Inf)
    val res = Source.single( (params, ()) ).via(dandelionFlow).runWith(Sink.head)

    res.map(_._1)
  }
}
