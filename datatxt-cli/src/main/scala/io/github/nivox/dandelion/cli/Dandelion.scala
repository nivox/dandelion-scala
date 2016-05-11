package io.github.nivox.dandelion.cli

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Sink, Source}
import io.github.nivox.dandelion.core._
import io.github.nivox.dandelion.datatxt.nex.{ExtraInfo, ExtraTypes, NexAPI, NexResponse}
import io.github.nivox.dandelion.datatxt.sent.{SentAPI, SentResponse}
import io.github.nivox.dandelion.datatxt.{DandelionLang, DandelionSource}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scalaz.Scalaz._
import scalaz._

trait CliSource
case class CliSingleSource(source: DandelionSource) extends CliSource
case object CliTextStreamSource extends CliSource


trait CommandConf
case class NexCommandConf(source: CliSource = null,
                          lang: Option[DandelionLang] = None,
                          minConfidence: Option[Float] = None,
                          minLength: Option[Int] = None,
                          socialHashtag: Option[Boolean] = None,
                          socialMention: Option[Boolean] = None,
                          extraInfo: Set[ExtraInfo] = Set(),
                          extraTypes: Set[ExtraTypes] = Set(),
                          country: Option[String] = None,
                          customSpots: Option[String] = None,
                          epsilon: Option[Float] = None
                         ) extends CommandConf {
  def withExtraInfo(i: ExtraInfo): NexCommandConf =
    copy(extraInfo = extraInfo + i)

  def withExtraTypes(t: ExtraTypes): NexCommandConf =
    copy(extraTypes = extraTypes + t)
}

case class SentCommandConf(source: CliSource = null,
                           lang: Option[DandelionLang] = None
                          ) extends CommandConf

case class DandelionConf(apiKey: String = null, apiSecret: String = null, command: CommandConf = null) {
  def withCommand(c: CommandConf): DandelionConf =
    copy(command = c)

  def updatedCommand[T <: CommandConf](f: T => T): DandelionConf = {
    copy(command = f(command.asInstanceOf[T]))
  }
}


object Dandelion extends App {

  implicit val system = ActorSystem("test")
  implicit val mat = ActorMaterializer()

  val parser = new scopt.OptionParser[DandelionConf]("dandelion") {
    help("help")

    opt[String]('K', "key") required() action { case (key, c) => c.copy(apiKey = key) }
    opt[String]('S', "secret") required() action { case (secret, c) => c.copy(apiSecret = secret) }

    cmd("sent").
      action { case (_, c) => c.withCommand(SentCommandConf()) }.
      children(
        opt[String]('t', "text") action { case (text, c) =>
          c.updatedCommand[SentCommandConf](_.copy(source = CliSingleSource(DandelionSource.Text(text))))
        },
        opt[Unit]('T', "textStream") action { case (_, c) =>
          c.updatedCommand[SentCommandConf](_.copy(source = CliTextStreamSource))
        },
        opt[String]('u', "url") action { case (url, c) =>
          c.updatedCommand[SentCommandConf](_.copy(source = CliSingleSource(DandelionSource.Text(url))))
        },
        opt[String]('l', "lang") action { case (lang, c) =>
          c.updatedCommand[SentCommandConf](_.copy(lang = Some(DandelionLang.LangCode(lang))))
        }
      )

    cmd("nex").
      action { case (_, c) => c.withCommand(NexCommandConf()) }.
      children(
        opt[String]('t', "text") action { case (text, c) =>
          c.updatedCommand[NexCommandConf](_.copy(source = CliSingleSource(DandelionSource.Text(text))))
        },
        opt[Unit]('T', "textStream") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.copy(source = CliTextStreamSource))
        },
        opt[String]('u', "url") action { case (url, c) =>
          c.updatedCommand[NexCommandConf](_.copy(source = CliSingleSource(DandelionSource.Text(url))))
        },
        opt[String]('l', "lang") action { case (lang, c) =>
          c.updatedCommand[NexCommandConf](_.copy(lang = Some(DandelionLang.LangCode(lang))))
        },
        opt[Double]("minConfidence") action { case (confidence, c) =>
          c.updatedCommand[NexCommandConf](_.copy(minConfidence = confidence.toFloat.some))
        },
        opt[Int]("minLength") action { case (len, c) =>
          c.updatedCommand[NexCommandConf](_.copy(minLength = len.some))
        },
        opt[Unit]("socialHashtag") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.copy(socialHashtag = true.some))
        },
        opt[Unit]("socialMention") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.copy(socialMention = true.some))
        },
        opt[Unit]("types") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.withExtraInfo(ExtraInfo.Types))
        },
        opt[Unit]("categories") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.withExtraInfo(ExtraInfo.Categories))
        },
        opt[Unit]("abstract") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.withExtraInfo(ExtraInfo.Abstract))
        },
        opt[Unit]("image") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.withExtraInfo(ExtraInfo.Image))
        },
        opt[Unit]("lod") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.withExtraInfo(ExtraInfo.Lod))
        },
        opt[Unit]("alternativeLabels") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.withExtraInfo(ExtraInfo.AlternateLabels))
        },
        opt[Unit]("phone") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.withExtraTypes(ExtraTypes.Phone))
        },
        opt[Unit]("vat") action { case (_, c) =>
          c.updatedCommand[NexCommandConf](_.withExtraTypes(ExtraTypes.Vat))
        },
        opt[String]("country") action { case (country, c) =>
          c.updatedCommand[NexCommandConf](_.copy(country = country.some))
        },
        opt[String]("customSpots") action { case (spots, c) =>
          c.updatedCommand[NexCommandConf](_.copy(customSpots = spots.some))
        },
        opt[Double]("epsilon") action { case (epsilon, c) =>
          c.updatedCommand[NexCommandConf](_.copy(epsilon = epsilon.toFloat.some))
        }
      )
  }

  def printUnitsInfo(maybeUnitsInfo: String \/ UnitsInfo): Unit = maybeUnitsInfo match {
    case \/-(unitsInfo) => System.err.println(s"Units: used=[${unitsInfo.cost}] left=[${unitsInfo.left}] uniapi-cost=[${unitsInfo.uniCost}] requestId=[${unitsInfo.requestId}]")
    case -\/(err) => System.err.println(s"Units: [ERROR] ${err}")
  }


  def handleNexResponse(text: String, resF: Future[EndpointError \/ EndpointResult[NexResponse]]): Unit = {
    val futureResult = \/.fromTryCatchNonFatal { Await.result(resF, Duration.Inf) }

    println(s"################ Entities extraction for: '${text}'")
    futureResult.map {
      case \/-(res) =>
        printUnitsInfo(res.unitsInfo)

        val data = res.data
        println(s"Timestamp: ${data.timestamp}")
        println(s"Time: ${data.time}")
        println(s"Lang: ${data.lang} [${data.langConfidence.getOrElse("NaN")}]")
        data.text.foreach(t => println(s"Text: ${t}"))

        println(s"Url: ${data.url.getOrElse("None")}")
        println(s"Annotations:")
        data.annotations.foreach { ann =>
          println(s" === ${ann.resource.title} [${ann.resource.uri}]")
          println(s"\t- Label: ${ann.label}")
          println(s"\t- Confidence${ann.confidence}")
          println(s"\t- Spot: ${ann.spot}")
          println(s"\t- Types: ${ann.types}")
          println(s"\t- Categories: ${ann.categories}")
          println(s"\t- Abstract: ${ann.abstractS.getOrElse("None")}")
          println(s"\t- Lod: ${ann.lod.getOrElse("None")}")
          println(s"\t- Alternate Labels: ${ann.alternateLabels}")
          println(s"\t- Image: ${ann.image.getOrElse("None")}")
        }
      case -\/(err) => println(s"[DANDELION ERROR] ${err.code}: ${err.message}")
    }.leftMap {
      case e: Throwable => println(s"[ERROR] Error receiving response: ${e.getMessage}")
    }
  }

  def handleSentResponse(text: String, resF: Future[EndpointError \/ EndpointResult[SentResponse]]): Unit = {
    val futureResult = \/.fromTryCatchNonFatal { Await.result(resF, Duration.Inf) }

    println(s"-- Sentiment for: '${text}'")
    futureResult.map {
      case \/-(res) =>
        printUnitsInfo(res.unitsInfo)
        println(s"Timestamp: ${res.data.timestamp}")
        println(s"Time: ${res.data.time}")
        println(s"Lang: ${res.data.lang}")
        println(s"Sentiment: ${res.data.sentiment.sentimentType}")
        println(s"Score: ${res.data.sentiment.score}")
      case -\/(err) => println(s"[DANDELION ERROR] ${err.code}: ${err.message}")
    }.leftMap {
      case e: Throwable => println(s"[ERROR] Error receiving response: ${e.getMessage}")
    }
  }


  def printSink[T](f: (String, Future[EndpointError \/ EndpointResult[T]]) => Unit): Sink[(Future[EndpointError \/ EndpointResult[T]], String), Future[Done]] = {
    Sink.foreach[(Future[EndpointError \/ EndpointResult[T]], String)] { case (respF, text) =>
        f(text, respF)
    }
  }

  parser.parse(args, DandelionConf()) match {
    case Some(cfg) =>
      val credentials = DandelionAuthCredentials(cfg.apiKey, cfg.apiSecret)
      implicit val api = DandelionAPI()

      val action: Future[_] = cfg.command match {
        case null => sys.error("No command specified!")

        case SentCommandConf(CliSingleSource(source), lang) =>
          val resF = SentAPI.sentiment(credentials, source, lang)
          handleSentResponse(source.toString, resF)
          Future.successful( () )

        case SentCommandConf(CliTextStreamSource, lang) =>
          val stdInSource = Source.fromIterator[String](() => scala.io.Source.fromInputStream(System.in, "UTF8").getLines()).map( text => DandelionSource.Text(text) -> text )
          val sentFlow = SentAPI.sentimentStream[String](credentials, lang)
          stdInSource.via(sentFlow).runWith(printSink(handleSentResponse))

        case c: NexCommandConf =>
          c.source match {
            case CliSingleSource(source) =>
              val resF = NexAPI.extractEntities(
                credentials,
                source,
                c.lang,
                c.minConfidence,
                c.minLength,
                c.socialHashtag,
                c.socialMention,
                c.extraInfo,
                c.extraTypes,
                c.country,
                c.customSpots,
                c.epsilon
              )
              handleNexResponse(source.toString, resF)
              Future.successful( () )

            case CliTextStreamSource =>
              val stdInSource = Source.fromIterator[String](() => scala.io.Source.fromInputStream(System.in, "UTF8").getLines()).map( text => DandelionSource.Text(text) -> text )
              val nexFlow = NexAPI.extractEntitiesStream[String](
                credentials,
                c.lang,
                c.minConfidence,
                c.minLength,
                c.socialHashtag,
                c.socialMention,
                c.extraInfo,
                c.extraTypes,
                c.country,
                c.customSpots,
                c.epsilon
              )
              stdInSource.via(nexFlow).runWith(printSink(handleNexResponse))
          }
      }

      Await.result(action, Duration.Inf)

      for {
        _ <- Http().shutdownAllConnectionPools()
        _ <- system.terminate()
      } yield ()
    case None => ()
  }
}
