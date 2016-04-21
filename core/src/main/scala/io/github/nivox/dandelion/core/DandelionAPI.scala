package io.github.nivox.dandelion.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import argonaut.Argonaut.jEmptyObject
import argonaut.{DecodeResult, Json}
import io.github.nivox.akka.http.argonaut.ArgonautSupport._
import io.github.nivox.dandelion.core.DandelionHeaders._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scalaz.Scalaz._
import scalaz._

class DandelionAPIException(message: String, cause: Throwable = null) extends Exception(message, cause)
class DandelionAPIUnitsInfoException(message: String) extends DandelionAPIException(message)

class DandelionAPIContentException(message: String) extends DandelionAPIException(message)
class DandelionAPIDataException(message: String) extends DandelionAPIContentException(message)
class DandelionAPIErrorException(message: String) extends DandelionAPIContentException(message)


object DandelionAPI {
  def apply(authority: Uri.Authority = Uri.Authority(Uri.Host("api.dandelion.eu"), 443))(implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): DandelionAPI =
    new DandelionAPI(authority)
}

class DandelionAPI(authority: Uri.Authority)(implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext) {
  val connPool = Http().cachedHostConnectionPoolHttps[Unit](authority.host.address, authority.port)

  private def request(req: HttpRequest): Future[HttpResponse] = {
    Source.single( (req, ()) ).
      via(connPool).runWith(Sink.head).
      flatMap {
        case (Success(resp), _) => Future.successful(resp)
        case (Failure(err), _) => Future.failed(err)
      }
  }

  private def extractUnitsInfo(resp: HttpResponse): String \/ UnitsInfo =
    for {
      cost <- resp.header[DandelionUnits] toRightDisjunction s"Missing or invalid ${DandelionUnits.name} header"
      left <- resp.header[DandelionUnitsLeft] toRightDisjunction s"Missing or invalid ${DandelionUnitsLeft.name} header"
      reset <- resp.header[DandelionUnitsReset] toRightDisjunction s"Missing or invalid ${DandelionUnitsReset.name} header"
    } yield UnitsInfo(cost.units, left.unitsLeft, reset.date)

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
    val resp = _resp.mapHeaders(decodeDanelionHeaders)

    val maybeUnitsInfo = extractUnitsInfo(resp)
    val maybeDataFuture = extractData(resp)

    maybeDataFuture.flatMap { maybeData =>
      val result = for {
        unitsInfo <- maybeUnitsInfo leftMap (new DandelionAPIUnitsInfoException(_))
        data <- maybeData leftMap (new DandelionAPIDataException(_))
      } yield EndpointResult(unitsInfo, data)

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



  def apiCall(credentials: AuthCredentials, servicePath: Uri.Path, params: FormData): Future[EndpointError \/ EndpointResult[Json]] = {
    val paramsWithAuth = params.copy(
      fields = ("$app_id", credentials.appId) +:
        ("$app_key", credentials.appKey) +:
        params.fields
    )

    val req = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri().withPath(servicePath),
      entity = paramsWithAuth.toEntity
    )

    val respF = request(req)
    respF.flatMap {
      case resp if resp.status == StatusCodes.OK => handleSuccessfulResponse(resp)
      case resp => handleErrorResponse(resp)
    }
  }
}
