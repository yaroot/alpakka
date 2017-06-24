/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.s3

import akka.actor.ActorSystem
import akka.stream.alpakka.s3.auth.AWSCredentials
import com.typesafe.config.Config

final case class Proxy(host: String, port: Int, scheme: String)

final case class S3Settings(bufferType: BufferType,
                            diskBufferPath: String,
                            proxy: Option[Proxy],
                            awsCredentials: AWSCredentials,
                            s3Region: String,
                            s3Endpoint: Option[String],
                            secureAccess: Boolean,
                            pathStyleAccess: Boolean) {

  override def toString: String =
    s"S3Settings($bufferType,$diskBufferPath,$proxy,$awsCredentials,$s3Region,$s3Endpoint,$pathStyleAccess)"
}

sealed trait BufferType

case object MemoryBufferType extends BufferType {
  def getInstance: BufferType = MemoryBufferType
}

case object DiskBufferType extends BufferType {
  def getInstance: BufferType = DiskBufferType
}

object S3Settings {
  val DEFAULT_PATH = "akka.stream.alpakka.s3"

  /**
   * Scala API: Creates [[S3Settings]] from the [[Config]] attached to an [[ActorSystem]].
   */
  def apply()(implicit system: ActorSystem): S3Settings = apply(system.settings.config.getConfig(DEFAULT_PATH))

  /**
   * Scala API: Creates [[S3Settings]] from a [[Config]] object.
   */
  def apply(config: Config): S3Settings = new S3Settings(
    bufferType = config.getString("buffer") match {
      case "memory" => MemoryBufferType
      case "disk" => DiskBufferType
      case _ => throw new IllegalArgumentException("Buffer type must be 'memory' or 'disk'")
    },
    diskBufferPath = config.getString("disk-buffer-path"),
    proxy = {
      if (config.getString("proxy.host") != "") {
        val scheme = if (config.getBoolean("proxy.secure")) "https" else "http"
        Some(
          Proxy(config.getString("proxy.host"),
                config.getInt("proxy.port"),
                scheme)
        )
      } else None
    },
    awsCredentials = AWSCredentials(config.getString("aws.access-key-id"),
                                    config.getString("aws.secret-access-key")),
    s3Region = config.getString("aws.default-region"),
    s3Endpoint = Option("aws.endpoint").filter(config.hasPath).map(config.getString),
    secureAccess = config.getBoolean("secure-access"),
    pathStyleAccess = config.getBoolean("path-style-access")
  )

  /**
   * Java API: Creates [[S3Settings]] from the [[Config]] attached to an [[ActorSystem]].
   */
  def create(system: ActorSystem) = apply()(system)

  /**
   * Java API: Creates [[S3Settings]] from a [[Config]].
   */
  def create(config: Config) = apply(config)
}
