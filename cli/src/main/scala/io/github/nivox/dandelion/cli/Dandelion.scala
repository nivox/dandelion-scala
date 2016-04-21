package io.github.nivox.dandelion.cli

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import io.github.nivox.dandelion.core.{AuthCredentials, DandelionAPI, EndpointError, EndpointResult}
import io.github.nivox.dandelion.datatxt.nex.{ExtraInfo, ExtraTypes, NexAPI}
import io.github.nivox.dandelion.datatxt.sent.SentAPI
import io.github.nivox.dandelion.datatxt.{Lang, Source}

import scalaz._
import Scalaz._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

trait CommandConf
case class NexCommandConf(source: Source = null,
                          lang: Option[Lang] = None,
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

case class SentCommandConf(source: Source = null,
                           lang: Option[Lang] = None
                          ) extends CommandConf

case class DandelionConf(apiKey: String = null, apiSecret: String = null, command: CommandConf = null) {
  def withCommand(c: CommandConf): DandelionConf =
    copy(command = c)

  def updatedCommand[T <: CommandConf](f: T => T): DandelionConf = {
    copy(command = f(command.asInstanceOf[T]))
  }
}


object Dandelion extends App {
  val parser = new scopt.OptionParser[DandelionConf]("dandelion") {
    help("help")

    opt[String]('K', "key") action { case (key, c) => c.copy(apiKey = key) }
    opt[String]('S', "secret") action { case (secret, c) => c.copy(apiSecret = secret) }

    cmd("sent").
      action { case (_, c) => c.withCommand(SentCommandConf()) }.
      children(
        opt[String]('t', "text") action { case (text, c) =>
          c.updatedCommand[SentCommandConf](_.copy(source = Source.Text(text)))
        },
        opt[String]('u', "url") action { case (url, c) =>
          c.updatedCommand[SentCommandConf](_.copy(source = Source.Text(url)))
        },
        opt[String]('l', "lang") action { case (lang, c) =>
          c.updatedCommand[SentCommandConf](_.copy(lang = Some(Lang.LangCode(lang))))
        }
      )

    cmd("nex").
      action { case (_, c) => c.withCommand(NexCommandConf()) }.
      children(
        opt[String]('t', "text") action { case (text, c) =>
          c.updatedCommand[NexCommandConf](_.copy(source = Source.Text(text)))
        },
        opt[String]('u', "url") action { case (url, c) =>
          c.updatedCommand[NexCommandConf](_.copy(source = Source.Text(url)))
        },
        opt[String]('l', "lang") action { case (lang, c) =>
          c.updatedCommand[NexCommandConf](_.copy(lang = Some(Lang.LangCode(lang))))
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

  parser.parse(args, DandelionConf()) match {
    case Some(cfg) =>
      implicit val system = ActorSystem("test")
      implicit val mat = ActorMaterializer()

      val credentials = AuthCredentials(cfg.apiKey, cfg.apiSecret)
      implicit val api = DandelionAPI()

      val action = cfg.command match {
        case SentCommandConf(source, lang) =>
          SentAPI.sentiment(credentials, source, lang) map {
            case \/-(EndpointResult(unitsInfo, data)) =>
              System.err.println(s"Units: used=[${unitsInfo.cost}] left=[${unitsInfo.left}] reset=[${unitsInfo.reset}]")
              println(s"Timestamp: ${data.timestamp}")
              println(s"Time: ${data.time}")
              println(s"Lang: ${data.lang}")
              println(s"Sentiment: ${data.sentiment.sentimentType}")
              println(s"Score: ${data.sentiment.score}")

            case -\/(EndpointError(message, code, _)) =>
              System.err.println(s"Error [${code}]: ${message}")
          }

        case c: NexCommandConf =>
          NexAPI.extractEntities(
            credentials,
            c.source,
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
          ) map {
            case \/-(EndpointResult(unitsInfo, data)) =>
              System.err.println(s"Units: used=[${unitsInfo.cost}] left=[${unitsInfo.left}] reset=[${unitsInfo.reset}]")
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

            case -\/(EndpointError(message, code, _)) =>
              System.err.println(s"Error [${code}]: ${message}")
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
