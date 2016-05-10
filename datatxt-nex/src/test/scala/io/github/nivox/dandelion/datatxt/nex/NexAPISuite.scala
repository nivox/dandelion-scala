package io.github.nivox.dandelion.datatxt.nex

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import io.github.nivox.dandelion.core.{DandelionAuthCredentials, DandelionAPI}
import io.github.nivox.dandelion.datatxt.{DandelionLang, DandelionSource}
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class NexAPISuite extends FunSuite {

  test("test") {
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val system = ActorSystem("test")
    implicit val mat = ActorMaterializer()

    val credentials = DandelionAuthCredentials("5585d158", "4b9fe5a44f352e8496fe62c0949972e5")
    implicit val api = DandelionAPI()

    val r = NexAPI.extractEntities(credentials, DandelionSource.Text("This is a test of Britain yadda Ferrari"), lang = Some(DandelionLang.Auto))
    val res = Await.result(r, Duration.Inf)
    println(res)
  }
}
