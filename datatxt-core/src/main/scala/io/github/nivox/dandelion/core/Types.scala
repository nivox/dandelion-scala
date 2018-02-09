package io.github.nivox.dandelion.core

import java.time.ZonedDateTime

import akka.http.scaladsl.model.FormData
import argonaut.Json

import scalaz.\/

sealed trait DandelionAuthCredentials {
  def toFormData: FormData
}

case class DandelionAppKeysAuthCredentials(appId: String, appKey: String) extends DandelionAuthCredentials {
  override def toFormData: FormData = FormData("$app_id" -> appId, "$app_key" -> appKey)
}

case class DandelionTokenAuthCredentials(token: String) extends DandelionAuthCredentials {
  override def toFormData: FormData = FormData("token" -> token)
}

case class UnitsInfo(cost: Float, left: Float, uniCost: Float, unitsResetTime: ZonedDateTime, requestId: String)

trait DandelionError
object DandelionError {
  case object NotFound extends DandelionError
  case object MissingParameter extends DandelionError
  case object UnknownParameter extends DandelionError
  case object InvalidParameter extends DandelionError
  case object RequestURITooLong extends DandelionError
  case object MethodNotAllowed extends DandelionError
  case object RequestTooLarge extends DandelionError
  case object AuthenticationError extends DandelionError
  case object NotAllowed extends DandelionError
  case object InternalServerError extends DandelionError
  case object BadGateway extends DandelionError
  case object RateLimitExceeded extends DandelionError

  case class UnknownError(code: String) extends DandelionError

  def fromCode(code: String): DandelionError = code.toLowerCase match {
    case "error.notfound" => NotFound
    case "error.missingparameter" => MissingParameter
    case "error.unknownparameter" => UnknownParameter
    case "error.invalidparameter" => InvalidParameter
    case "error.requesturitoolong" => RequestURITooLong
    case "error.methodnotallowed" => MethodNotAllowed
    case "error.requesttooLarge" => RequestTooLarge
    case "error.authenticationerror" => AuthenticationError
    case "error.notallowed" => NotAllowed
    case "error.internalservererror" => InternalServerError
    case "error.badgateway" => BadGateway
    case "error.ratelimitexceeded" => RateLimitExceeded
    case _ => UnknownError(code)
  }
}

case class EndpointResult[T](unitsInfo: String \/ UnitsInfo, data: T)

sealed trait ApiCallError
case class EndpointError(message: String, code: DandelionError, data: Json) extends ApiCallError {
  override def toString: String = s"EnpointError(${message}, ${code}, ${data})"
}
case class CallException(message: String, cause: Throwable) extends Exception(message, cause) with ApiCallError


