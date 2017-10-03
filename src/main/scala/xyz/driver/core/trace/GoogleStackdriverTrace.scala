package xyz.driver.core.trace

import java.io.FileInputStream
import java.nio.file.{Files, Paths}
import java.util

import akka.http.scaladsl.model.HttpRequest
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.trace.grpc.v1.GrpcTraceConsumer
import com.google.cloud.trace.v1.consumer.TraceConsumer
import com.typesafe.scalalogging.Logger

final class GoogleStackdriverTrace(projectId: String,
                                   clientSecretsFile: String,
                                   appName: String,
                                   appEnvironment: String,
                                   log: Logger,
                                   bufferSize: Int = 10)
    extends GoogleServiceTracer {

  // initialize our various tracking storage systems
  private val clientSecretsInputStreamOpt: Option[FileInputStream] = if (Files.exists(Paths.get(clientSecretsFile))) {
    Some(new FileInputStream(clientSecretsFile))
  } else {
    None
  }
  // if the google credentials are invalid, just log the traces
  private val traceConsumer: TraceConsumer = clientSecretsInputStreamOpt.fold[TraceConsumer] {
    log.warn(s"Google credentials not found in path: $clientSecretsFile")
    new LoggingTraceConsumer(log)
  } { clientSecretsInputStream =>
    GrpcTraceConsumer
      .createWithCredentials(
        GoogleCredentials
          .fromStream(clientSecretsInputStream)
          .createScoped(util.Arrays.asList("https://www.googleapis.com/auth/trace.append"))
      )
  }

  private val googleServiceTracer =
    new GoogleStackdriverTraceWithConsumer(projectId, appName, appEnvironment, traceConsumer, log, bufferSize)

  override def startSpan(httpRequest: HttpRequest): GoogleStackdriverTraceSpan =
    googleServiceTracer.startSpan(httpRequest)

  override def endSpan(span: GoogleStackdriverTraceSpan): Unit = googleServiceTracer.endSpan(span)

  override def flush(): Unit = googleServiceTracer.flush()
}
