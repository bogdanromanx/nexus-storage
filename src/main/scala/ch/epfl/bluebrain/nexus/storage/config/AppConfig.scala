package ch.epfl.bluebrain.nexus.storage.config

import java.nio.file.Path

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.commons.kamon.directives.TracingDirectives
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.{Anonymous, Subject, User}
import ch.epfl.bluebrain.nexus.storage.config.AppConfig._

/**
  * Application configuration
  *
  * @param description service description
  * @param http        http interface configuration
  * @param storage     storages configuration
  * @param subject     allowed subject to perform calls to this service
  * @param iam         iam client configuration
  */
final case class AppConfig(description: Description,
                           http: HttpConfig,
                           storage: StorageConfig,
                           subject: SubjectConfig,
                           iam: IamClientConfig)

object AppConfig {

  /**
    * Service description
    *
    * @param name service name
    */
  final case class Description(name: String) {

    /**
      * @return the version of the service
      */
    val version: String = BuildInfo.version

    /**
      * @return the full name of the service (name + version)
      */
    val fullName: String = s"$name-${version.replaceAll("\\W", "-")}"

  }

  /**
    * HTTP configuration
    *
    * @param interface  interface to bind to
    * @param port       port to bind to
    * @param prefix     prefix to add to HTTP routes
    * @param publicUri  public URI of the service
    */
  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri)

  /**
    * Storages configuration
    *
    * @param rootVolume         the base [[Path]] where the files are stored
    * @param protectedDirectory the relative [[Path]] of the protected directory once the storage bucket is selected
    * @param algorithm          the storage file algorithm
    */
  final case class StorageConfig(rootVolume: Path, protectedDirectory: Path, algorithm: String)

  /**
    * Allowed subject to perform calls to this service
    *
    * @param anonymous flag to decide whether or not the allowed subject is Anonymous or a User
    * @param realm     the user realm. It must be present when anonymous = false and it must be removed when anonymous = true
    * @param name      the user name. It must be present when anonymous = false and it must be removed when anonymous = true
    */
  final case class SubjectConfig(anonymous: Boolean, realm: Option[String], name: Option[String]) {
    val subjectValue: Subject = (anonymous, realm, name) match {
      case (false, Some(r), Some(s)) => User(s, r)
      case (false, _, _) =>
        throw new IllegalArgumentException(
          "subject configuration is wrong. When anonymous is set to false, a realm and a subject must be provided")
      case (true, None, None) => Anonymous
      case _ =>
        throw new IllegalArgumentException(
          "subject configuration is wrong. When anonymous is set to true, a realm and a subject should not be present")
    }
  }

  implicit def toStorage(implicit config: AppConfig): StorageConfig = config.storage
  implicit def toHttp(implicit config: AppConfig): HttpConfig       = config.http
  implicit def toIam(implicit config: AppConfig): IamClientConfig   = config.iam

  val orderedKeys = OrderedKeys(
    List(
      "@context",
      "@id",
      "@type",
      "reason",
      "message",
      "details",
      "filename",
      "location",
      "bytes",
      ""
    ))

  val tracing = new TracingDirectives()
}
