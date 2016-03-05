package io.github.nivox.dandelion.core

import java.text.SimpleDateFormat

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import argonaut.Argonaut.jEmptyObject
import argonaut.{DecodeResult, Json}
import io.github.nivox.akka.http.argonaut.ArgonautSupport._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scalaz.Scalaz._
import scalaz._

class DandelionAPIException(message: String, cause: Throwable = null) extends Exception(message, cause)
class DandelionAPIUnitsInfoException(message: String) extends DandelionAPIException(message)

class DandelionAPIContentException(message: String) extends Exception
class DandelionAPIDataException(message: String) extends DandelionAPIContentException(message)
class DandelionAPIErrorException(message: String) extends DandelionAPIContentException(message)


object DandelionAPI {
  def apply()(implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext) = new DandelionAPI
}

class DandelionAPI(implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext) {
  val endpoint = Uri("https://api.dandelion.eu:443")

  val connPool = Http().cachedHostConnectionPoolTls[Unit](endpoint.authority.host.address, endpoint.effectivePort)
  val resetDateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss Z")

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
      costH <- resp.getHeader("X-DL-units").asScala toRightDisjunction "No X-DL-units header"
      cost <- \/.fromTryCatch(Integer.parseInt(costH.value)) leftMap (_ => s"Invalid X-DL-units: ${costH.value}")
      leftH <- resp.getHeader("X-DL-units-left").asScala toRightDisjunction "No X-DL-units-left header"
      left <- \/.fromTryCatch(Integer.parseInt(leftH.value)) leftMap (_ => s"Invalid X-DL-units: ${costH.value}")
      resetH <- resp.getHeader("X-DL-units-reset").asScala toRightDisjunction "No X-DL-units-reset header"
      resetDate = resetDateFormat.parse(resetH.value)
    } yield UnitsInfo(cost, left, resetDate)

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


  private def handleSuccessfulResponse(resp: HttpResponse): Future[EndpointError \/ EndpointResult] = {
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

  private def handleErrorResponse(resp: HttpResponse): Future[EndpointError \/ EndpointResult] = {
    val maybeErrorFuture = extractError(resp)

    maybeErrorFuture.flatMap { maybeError =>
      maybeError.fold(
        (err) => Future.failed(new DandelionAPIErrorException(err)),
        (succ) => Future.successful(succ.left)
      )
    }
  }



  def apiCall(credentials: AuthCredentials, servicePath: Uri.Path, params: FormData): Future[EndpointError \/ EndpointResult] = {
    val paramsWithAuth = params.copy(
      fields = ("$app_id", credentials.appId) +:
        ("$app_key", credentials.appKey) +:
        params.fields
    )

    val req = HttpRequest(method = HttpMethods.POST, uri = endpoint.withPath(servicePath), entity = paramsWithAuth.toEntity)

    val respF = request(req)
    respF.flatMap {
      case resp if resp.status == StatusCodes.OK => handleSuccessfulResponse(resp)
      case resp => handleErrorResponse(resp)
    }
  }
}
