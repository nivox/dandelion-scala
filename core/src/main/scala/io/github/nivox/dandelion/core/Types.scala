package io.github.nivox.dandelion.core

import argonaut.Json

import scalaz.\/

case class DandelionAuthCredentials(appId: String, appKey: String)
case class UnitsInfo(cost: Float, left: Float, uniCost: Float, requestId: String)

trait DandelionError
object DandelionError {
  object NotFound extends DandelionError
  object MissingParameter extends DandelionError
  object UnknownParameter extends DandelionError
  object InvalidParameter extends DandelionError
  object RequestURITooLong extends DandelionError
  object MethodNotAllowed extends DandelionError
  object RequestTooLarge extends DandelionError
  object AuthenticationError extends DandelionError
  object InternalServerError extends DandelionError
  object BadGateway extends DandelionError
  case class UnknownError(code: String) extends DandelionError

  def fromCode(code: String): DandelionError = code match {
    case "error.notFound" => NotFound
    case "error.missingParameter" => MissingParameter
    case "error.unknownParameter" => UnknownParameter
    case "error.invalidParameter" => InvalidParameter
    case "error.requestURITooLong" => RequestURITooLong
    case "error.methodNotAllowed" => MethodNotAllowed
    case "error.requestTooLarge" => RequestTooLarge
    case "error.authenticationError" => AuthenticationError
    case "error.internalServerError" => InternalServerError
    case "error.badGateway" => BadGateway
    case _ => UnknownError(code)
  }
}

case class EndpointResult[T](unitsInfo: String \/ UnitsInfo, data: T)
case class EndpointError(message: String, code: DandelionError, data: Json)
