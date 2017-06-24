/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.alpakka.s3.impl

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{ContentTypes, RequestEntity, _}
import akka.stream.alpakka.s3.S3Settings
import akka.stream.scaladsl.Source
import akka.util.ByteString
import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

private[alpakka] object HttpRequests {

  def listBucket(
      bucket: String,
      prefix: Option[String] = None,
      continuationToken: Option[String] = None
  )(implicit conf: S3Settings): HttpRequest = {

    val query = Query(
      Seq(
        "list-type" -> Some("2"),
        "prefix" -> prefix,
        "continuation-token" -> continuationToken
      ).collect { case (k, Some(v)) => k -> v }.toMap
    )

    HttpRequest(HttpMethods.GET)
      .withHeaders(Host(requestHost(bucket)))
      .withUri(requestUri(bucket, None).withQuery(query))
  }

  def getDownloadRequest(s3Location: S3Location)(implicit conf: S3Settings): HttpRequest =
    s3Request(s3Location)

  def initiateMultipartUploadRequest(s3Location: S3Location, contentType: ContentType, s3Headers: S3Headers)(
      implicit conf: S3Settings
  ): HttpRequest =
    s3Request(s3Location, HttpMethods.POST, _.withQuery(Query("uploads")))
      .withDefaultHeaders(s3Headers.headers: _*)
      .withEntity(HttpEntity.empty(contentType))

  def uploadPartRequest(upload: MultipartUpload, partNumber: Int, payload: Source[ByteString, _], payloadSize: Int)(
      implicit conf: S3Settings
  ): HttpRequest =
    s3Request(
      upload.s3Location,
      HttpMethods.PUT,
      _.withQuery(Query("partNumber" -> partNumber.toString, "uploadId" -> upload.uploadId))
    ).withEntity(HttpEntity(ContentTypes.`application/octet-stream`, payloadSize, payload))

  def completeMultipartUploadRequest(upload: MultipartUpload, parts: Seq[(Int, String)])(
      implicit ec: ExecutionContext,
      conf: S3Settings
  ): Future[HttpRequest] = {

    //Do not let the start PartNumber,ETag and the end PartNumber,ETag be on different lines
    //  They tend to get split when this file is formatted by IntelliJ unless http://stackoverflow.com/a/19492318/1216965
    // @formatter:off
    val payload = <CompleteMultipartUpload>
                    {
                      parts.map { case (partNumber, etag) => <Part><PartNumber>{ partNumber }</PartNumber><ETag>{ etag }</ETag></Part> }
                    }
                  </CompleteMultipartUpload>
    // @formatter:on
    for {
      entity <- Marshal(payload).to[RequestEntity]
    } yield {
      s3Request(
        upload.s3Location,
        HttpMethods.POST,
        _.withQuery(Query("uploadId" -> upload.uploadId))
      ).withEntity(entity)
    }
  }

  private[this] def s3Request(s3Location: S3Location,
                              method: HttpMethod = HttpMethods.GET,
                              uriFn: (Uri => Uri) = identity)(implicit conf: S3Settings): HttpRequest =
    HttpRequest(method)
      .withHeaders(Host(requestHost(s3Location.bucket)))
      .withUri(uriFn(requestUri(s3Location.bucket, Some(s3Location.key))))

  private[this] def requestHost(bucket: String)(implicit conf: S3Settings): Uri.Host =
    conf.proxy match {
      case None =>
        val pathStyle = conf.pathStyleAccess
        val region = conf.s3Region
        val host = conf.s3Endpoint match {
          case Some(endpoint) => endpoint
          case None if region == "us-east-1" =>
            if (pathStyle) "s3.amazonaws.com" else s"$bucket.s3.amazonaws.com"
          case None if region == "cn-north-1" =>
            if (pathStyle) "s3.cn-north-1.amazonaws.com.cn" else s"$bucket.s3.cn-north-1.amazonaws.com.cn"
          case None =>
            if (pathStyle) s"s3-$region.amazonaws.com" else s"$bucket.s3-$region.amazonaws.com"
        }
        Uri.Host(host)
      case Some(proxy) => Uri.Host(proxy.host)
    }

  private[this] def requestUri(bucket: String, key: Option[String])(implicit conf: S3Settings): Uri = {
    val keyPath = key.map("/" + _).getOrElse("")
    val path = if (conf.pathStyleAccess) s"/$bucket$keyPath" else keyPath
    val uri = Uri(path).withHost(requestHost(bucket))

    //    val uri = if (conf.pathStyleAccess) {
    //      Uri(s"/$bucket${key.fold("")((someKey) => s"/$someKey")}")
    //        .withHost(requestHost(bucket))
    //    } else {
    //      Uri(s"${key.fold("")(someKey => s"/$someKey")}").withHost(requestHost(bucket))
    //    }
    conf.proxy match {
      case None =>
        val scheme = if (conf.secureAccess) "https" else "http"
        uri.withScheme(scheme)
      case Some(proxy) => uri.withPort(proxy.port).withScheme(proxy.scheme)
    }
  }
}
