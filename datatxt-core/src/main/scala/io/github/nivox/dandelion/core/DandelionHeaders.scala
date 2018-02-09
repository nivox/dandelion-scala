package io.github.nivox.dandelion.core

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion, RawHeader}

import scala.util.Try

object DandelionHeaders {

  val UNITS_HEADER = "X-Dl-Units"
  val UNIAPI_COST_HEADER = "X-Uniapi-Cost"
  val UNITS_LEFT_HEADER = "X-Dl-Units-Left"
  val UNITS_RESET_HEADER = "X-Dl-Units-Reset"
  val REQUEST_ID_HEADER = "X-Dl-Request-Id"

  def decodeDandelionHeaders(headers: collection.immutable.Seq[HttpHeader]): collection.immutable.Seq[HttpHeader] =
    headers.map {
      case h: RawHeader => h.name match {
        case UNITS_HEADER => DandelionUnits.parse(h.value).getOrElse(h)
        case UNIAPI_COST_HEADER => DandelionUniApiCost.parse(h.value).getOrElse(h)
        case UNITS_LEFT_HEADER => DandelionUnitsLeft.parse(h.value).getOrElse(h)
        case UNITS_RESET_HEADER => DandelionUnitsReset.parse(h.value).getOrElse(h)
        case REQUEST_ID_HEADER => DandelionRequestId.parse(h.value).getOrElse(h)
        case _ => h
      }

      case h => h
    }

  final class DandelionUnits(val units: Float) extends ModeledCustomHeader[DandelionUnits] {
    override def companion: ModeledCustomHeaderCompanion[DandelionUnits] = DandelionUnits
    override def value(): String = units.toString
    override def renderInResponses(): Boolean = true
    override def renderInRequests(): Boolean = false
  }

  object DandelionUnits extends ModeledCustomHeaderCompanion[DandelionUnits] {
    override def name: String = UNITS_HEADER
    override def parse(value: String): Try[DandelionUnits] = Try {
      new DandelionUnits(value.toFloat)
    }
  }


  final class DandelionUnitsLeft(val unitsLeft: Float) extends ModeledCustomHeader[DandelionUnitsLeft] {
    override def companion: ModeledCustomHeaderCompanion[DandelionUnitsLeft] = DandelionUnitsLeft
    override def value(): String = unitsLeft.toString
    override def renderInResponses(): Boolean = true
    override def renderInRequests(): Boolean = false
  }

  object DandelionUnitsLeft extends ModeledCustomHeaderCompanion[DandelionUnitsLeft] {
    override def name: String = UNITS_LEFT_HEADER
    override def parse(value: String): Try[DandelionUnitsLeft] = Try {
      new DandelionUnitsLeft(value.toFloat)
    }
  }

  final class DandelionUniApiCost(val uniapiCost: Float) extends ModeledCustomHeader[DandelionUniApiCost] {
    override def companion: ModeledCustomHeaderCompanion[DandelionUniApiCost] = DandelionUniApiCost
    override def value(): String = uniapiCost.toString
    override def renderInResponses(): Boolean = true
    override def renderInRequests(): Boolean = false
  }

  object DandelionUniApiCost extends ModeledCustomHeaderCompanion[DandelionUniApiCost] {
    val unitsPtr = """\s*units=(\S+)\s*$""".r
    override def name: String = UNIAPI_COST_HEADER
    override def parse(value: String): Try[DandelionUniApiCost] = Try {
      unitsPtr.findFirstMatchIn(value).map { uniCost =>
        new DandelionUniApiCost(uniCost.group(1).toFloat)
      } getOrElse( sys.error(s"Unsupported ${name} header value: " + value) )
    }
  }

  final class DandelionUnitsReset(val resetTime: ZonedDateTime) extends ModeledCustomHeader[DandelionUnitsReset] {
    override def companion: ModeledCustomHeaderCompanion[DandelionUnitsReset] = DandelionUnitsReset
    override def value(): String = DandelionUnitsReset.dateFormat.format(resetTime)
    override def renderInResponses(): Boolean = true
    override def renderInRequests(): Boolean = false
  }

  object DandelionUnitsReset extends ModeledCustomHeaderCompanion[DandelionUnitsReset] {
    val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
    override def name: String = UNITS_RESET_HEADER
    override def parse(value: String): Try[DandelionUnitsReset] = {
      val t = Try {
        val resetDate = ZonedDateTime.parse(value, dateFormat)
        new DandelionUnitsReset(resetDate)
      }

      t
    }
  }

  final class DandelionRequestId(val requestId: String) extends ModeledCustomHeader[DandelionRequestId] {
    override def companion: ModeledCustomHeaderCompanion[DandelionRequestId] = DandelionRequestId
    override def value(): String = requestId
    override def renderInResponses(): Boolean = true
    override def renderInRequests(): Boolean = false
  }

  object DandelionRequestId extends ModeledCustomHeaderCompanion[DandelionRequestId] {
    override def name: String = REQUEST_ID_HEADER
    override def parse(value: String): Try[DandelionRequestId] = Try {
      new DandelionRequestId(value)
    }
  }  
}
