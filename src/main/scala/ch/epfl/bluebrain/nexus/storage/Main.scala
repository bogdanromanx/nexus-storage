package ch.epfl.bluebrain.nexus.storage

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import cats.effect.Effect
import ch.epfl.bluebrain.nexus.iam.client.IamClient
import ch.epfl.bluebrain.nexus.storage.Storages.DiskStorage
import ch.epfl.bluebrain.nexus.storage.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.storage.config.AppConfig._
import ch.epfl.bluebrain.nexus.storage.routes.Routes
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.system.SystemMetrics
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

//noinspection TypeAnnotation
// $COVERAGE-OFF$
object Main {

  def loadConfig(): Config = {
    val cfg = sys.env.get("STORAGE_CONFIG_FILE") orElse sys.props.get("storage.config.file") map { str =>
      val file = Paths.get(str).toAbsolutePath.toFile
      ConfigFactory.parseFile(file)
    } getOrElse ConfigFactory.empty()
    (cfg withFallback ConfigFactory.load()).resolve()
  }

  def setupMonitoring(config: Config): Unit = {
    Kamon.reconfigure(config)
    SystemMetrics.startCollecting()
    Kamon.loadReportersFromConfig()
  }

  @SuppressWarnings(Array("UnusedMethodParameter"))
  def main(args: Array[String]): Unit = {
    val config = loadConfig()
    setupMonitoring(config)

    implicit val appConfig: AppConfig = Settings(config).appConfig

    implicit val as: ActorSystem            = ActorSystem(appConfig.description.fullName, config)
    implicit val ec: ExecutionContext       = as.dispatcher
    implicit val mt: Materializer           = ActorMaterializer()
    implicit val eff: Effect[Task]          = Task.catsEffect(Scheduler.global)
    implicit val iamClient: IamClient[Task] = IamClient[Task]

    val storages: Storages[AkkaSource] = new DiskStorage(appConfig.storage)

    val logger: LoggingAdapter = Logging(as, getClass)

    logger.info("==== Cluster is Live ====")
    val routes: Route = Routes(storages)

    val httpBinding: Future[Http.ServerBinding] = {
      Http().bindAndHandle(routes, appConfig.http.interface, appConfig.http.port)
    }
    httpBinding onComplete {
      case Success(binding) =>
        logger.info(s"Bound to ${binding.localAddress.getHostString}: ${binding.localAddress.getPort}")
      case Failure(th) =>
        logger.error(th, "Failed to perform an http binding on {}:{}", appConfig.http.interface, appConfig.http.port)
        Await.result(as.terminate(), 10 seconds)
    }

    as.registerOnTermination {
      Kamon.stopAllReporters()
      SystemMetrics.stopCollecting()
    }
    // attempt to leave the cluster before shutting down
    val _ = sys.addShutdownHook {
      Await.result(as.terminate().map(_ => ()), 10 seconds)
    }
  }
}
// $COVERAGE-ON$
