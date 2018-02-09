package io.github.nivox.dandelion.core

import java.time.temporal.ChronoUnit
import java.time.{LocalTime, ZoneOffset, ZonedDateTime}
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.FormData
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.stream.stage._

import scala.concurrent.duration._
import scalaz.{-\/, \/, \/-}

case class ApiCallRateLimitContext[CTX](formData: FormData, innerCtx: CTX)

class ApiCallRateLimitInStage[U] extends GraphStage[FanInShape2[(FormData, U), (FormData, U), (FormData, ApiCallRateLimitContext[U])]] {
  val in: Inlet[(FormData, U)] = Inlet("in-data")
  val retry: Inlet[(FormData, U)] = Inlet("retry-data")
  val out: Outlet[(FormData, ApiCallRateLimitContext[U])] = Outlet("out")

  override def shape: FanInShape2[(FormData, U), (FormData, U), (FormData, ApiCallRateLimitContext[U])] =
    new FanInShape2(in, retry, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    import scala.collection.mutable
    val retryBuffer = mutable.Queue[ApiCallRateLimitContext[U]]()
    var inClosed = false

    setHandler(in, new InHandler {
      override def onUpstreamFinish(): Unit = {
        inClosed = true
      }

      override def onPush(): Unit = {
        val (formData, ctx) = grab(in)
        val rateLimitCtx = ApiCallRateLimitContext(formData, ctx)
        if (retryBuffer.isEmpty) {
          emit(out, formData -> rateLimitCtx)
        } else {
          retryBuffer.enqueue(rateLimitCtx)
          val bufferedCtx = retryBuffer.dequeue()
          emit(out, bufferedCtx.formData -> bufferedCtx)
        }
      }
    })

    setHandler(retry, new InHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onUpstreamFinish(): Unit = {
      }

      override def onPush() = {
        val (formData, ctx) = grab(retry)
        val rateLimitCtx = ApiCallRateLimitContext(formData, ctx)

        retryBuffer.enqueue(rateLimitCtx)
        pull(retry)
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (retryBuffer.nonEmpty) {
          val ctx = retryBuffer.dequeue()
          emit(out, ctx.formData -> ctx)
        }

        if (!inClosed && retryBuffer.isEmpty) {
          pull(in)
        }

        if (inClosed && retryBuffer.isEmpty) {
          completeStage()
        }
      }
    })

    override def preStart(): Unit = {
      pull(retry)
    }


  }
}

class ApiCallRateLimitOutStage[T, U](log: LoggingAdapter, defaultResetTime: LocalTime, defaultZone: ZoneOffset) extends GraphStage[
  FanOutShape2[
    (ApiCallError \/ EndpointResult[T], ApiCallRateLimitContext[U]),
    (ApiCallError \/ EndpointResult[T], U),
    (FormData, U)]
  ]
{
  val in = Inlet[(\/[ApiCallError, EndpointResult[T]], ApiCallRateLimitContext[U])]("endpoint-res")
  val out = Outlet[(\/[ApiCallError, EndpointResult[T]], U)]("success")
  val retry = Outlet[(FormData, U)]("retry")

  override def shape: FanOutShape2[
    (\/[ApiCallError, EndpointResult[T]], ApiCallRateLimitContext[U]),
    (\/[ApiCallError, EndpointResult[T]], U),
    (FormData, U)
    ] = new FanOutShape2(in, out, retry)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {
    var resetTime: Option[ZonedDateTime] = None
    var waitingForResumeTimer = false

    def durationToUnitsRefresh: FiniteDuration = {
      val actualResetTime = resetTime getOrElse {
        val base = ZonedDateTime.now(defaultZone)
        val timed = base.`with`(defaultResetTime)

        if (base.isAfter(timed)) timed.plusDays(1)
        else timed
      }
      val currentTime = ZonedDateTime.now(actualResetTime.getOffset)

      val secondsLeftToRefresh = currentTime.until(actualResetTime, ChronoUnit.SECONDS)
      Duration(secondsLeftToRefresh, TimeUnit.SECONDS)
    }

    setHandler(in, new InHandler {
      override def onPush() = {
        val inD = grab(in)

        inD match {
          case (res@ \/-(d), ctx) =>
            resetTime = d.unitsInfo.toOption.map(_.unitsResetTime)
            emit(out, res -> ctx.innerCtx)

          case (-\/(CallException(msg, err)), ctx) =>
            log.error(err, s"Error during api call, retrying: ${err}")
            err.printStackTrace()
            emit(retry, ctx.formData -> ctx.innerCtx)

          case (-\/(EndpointError(_, DandelionError.NotAllowed, _)), ctx) =>
            val waitDuration = durationToUnitsRefresh
            log.warning(s"No dandelion units left. Waiting for refresh in ${waitDuration.toString()}")
            emit(retry, ctx.formData -> ctx.innerCtx)
            waitingForResumeTimer = true
            scheduleOnce("resume", waitDuration)

          case (-\/(EndpointError(_, DandelionError.RateLimitExceeded, _)), ctx) =>
            log.warning(s"Rate limit exceeded. Waiting 1 second before retrying")
            emit(retry, ctx.formData -> ctx.innerCtx)
            waitingForResumeTimer = true
            scheduleOnce("resume", 1.second)

          case (res@ -\/(_), ctx) =>
            emit(out, res -> ctx.innerCtx)
        }
      }
    })

    setHandler(out, new OutHandler {
      override def onPull() = {
        if (!waitingForResumeTimer) {
          pull(in)
        }
      }
    })

    setHandler(retry, new OutHandler {
      override def onPull() = {}
    })

    override protected def onTimer(timerKey: Any): Unit = timerKey match {
      case "resume" =>
        waitingForResumeTimer = false
        pull(in)
    }
  }
}

object ApiCallRateLimitFlow {
  def apply[T, U](_flow: Flow[(FormData, ApiCallRateLimitContext[U]), (\/[ApiCallError, EndpointResult[T]], ApiCallRateLimitContext[U]), NotUsed],
               loggingAdapter: LoggingAdapter,
               defaultResetTime: LocalTime = LocalTime.of(0, 0, 0), defaultZone: ZoneOffset = ZoneOffset.UTC)
  : Flow[(FormData, U), (\/[ApiCallError, EndpointResult[T]], U), NotUsed] = {
    Flow.fromGraph {
      GraphDSL.create(_flow) { implicit builder =>
        (flow) =>
          import GraphDSL.Implicits._

          val inStage = builder.add(new ApiCallRateLimitInStage[U]())
          val outStage = builder.add(new ApiCallRateLimitOutStage[T, U](loggingAdapter, defaultResetTime, defaultZone))

          inStage.out ~> flow.in
          flow.out ~> outStage.in
          outStage.out1 ~> inStage.in1

          FlowShape(inStage.in0, outStage.out0)
      }
    }
  }
}
