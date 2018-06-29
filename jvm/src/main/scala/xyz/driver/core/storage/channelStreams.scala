package xyz.driver.core.storage

import java.nio.ByteBuffer
import java.nio.channels.{ReadableByteChannel, WritableByteChannel}

import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage._
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

class ChannelSource(createChannel: () => ReadableByteChannel, chunkSize: Int)
    extends GraphStage[SourceShape[ByteString]] {

  val out   = Outlet[ByteString]("ChannelSource.out")
  val shape = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val channel = createChannel()

    object Handler extends OutHandler {
      override def onPull(): Unit = {
        try {
          val buffer = ByteBuffer.allocate(chunkSize)
          if (channel.read(buffer) > 0) {
            buffer.flip()
            push(out, ByteString.fromByteBuffer(buffer))
          } else {
            completeStage()
          }
        } catch {
          case NonFatal(_) =>
            channel.close()
        }
      }
      override def onDownstreamFinish(): Unit = {
        channel.close()
      }
    }

    setHandler(out, Handler)
  }

}

class ChannelSink(createChannel: () => WritableByteChannel, chunkSize: Int)
    extends GraphStageWithMaterializedValue[SinkShape[ByteString], Future[Done]] {

  val in    = Inlet[ByteString]("ChannelSink.in")
  val shape = SinkShape(in)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val promise = Promise[Done]()
    val logic = new GraphStageLogic(shape) {
      val channel = createChannel()

      object Handler extends InHandler {
        override def onPush(): Unit = {
          try {
            val data = grab(in)
            channel.write(data.asByteBuffer)
            pull(in)
          } catch {
            case NonFatal(e) =>
              channel.close()
              promise.failure(e)
          }
        }

        override def onUpstreamFinish(): Unit = {
          channel.close()
          completeStage()
          promise.success(Done)
        }

        override def onUpstreamFailure(ex: Throwable): Unit = {
          channel.close()
          promise.failure(ex)
        }
      }

      setHandler(in, Handler)

      override def preStart(): Unit = {
        pull(in)
      }
    }
    (logic, promise.future)
  }

}

object ChannelStream {

  def fromChannel(channel: () => ReadableByteChannel, chunkSize: Int = 8192): Source[ByteString, NotUsed] = {
    Source
      .fromGraph(new ChannelSource(channel, chunkSize))
      .withAttributes(Attributes(ActorAttributes.IODispatcher))
      .async
  }

  def toChannel(channel: () => WritableByteChannel, chunkSize: Int = 8192): Sink[ByteString, Future[Done]] = {
    Sink
      .fromGraph(new ChannelSink(channel, chunkSize))
      .withAttributes(Attributes(ActorAttributes.IODispatcher))
      .async
  }

}
