package arapp.photoframe

import java.util.Properties

import arapp.photoframe.googleoauth.{GoogleBearerToken, GoogleOauthCredentials}
import arapp.photoframe.twitterapi.TwitterBearerToken
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.google.inject.name.Named
import com.google.inject.{Module, Provides, Singleton}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Http, Service}
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.inject.TwitterModule
import com.twitter.inject.server.TwitterServer
import com.twitter.util.Timer

import scala.io.Source

object PhotoFrameModule extends TwitterModule {

  @Singleton
  @Provides
  @Named("GoogleHttpClient")
  def providesHttpClient(): Service[Request, Response] = {
      Http.client
        .withTls("www.googleapis.com")
        .newService("www.googleapis.com:443")
  }

  @Singleton
  @Provides
  @Named("PhotosApiHttpClient")
  def providesPhotosApiHttpClient(): Service[Request, Response] = {
    Http.client
      .withTls("photoslibrary.googleapis.com")
      .newService("photoslibrary.googleapis.com:443")
  }

  @Singleton
  @Provides
  @Named("TwitterApiHttpClient")
  def providesTwitterApiHttpClient(): Service[Request, Response] = {
    Http.client
      .withTls("api.twitter.com")
      .newService("api.twitter.com:443")
  }

  @Singleton
  @Provides
  @Named("TwitterPublishApiHttpClient")
  def providesTwitterPublishApiHttpClient(): Service[Request, Response] = {
    Http.client
      .withTls("publish.twitter.com")
      .newService("publish.twitter.com:443")
  }

  @Singleton
  @Provides
  @Named("CamelCaseObjectMapper")
  def providesObjectMapper(): ScalaObjectMapper = {
      ScalaObjectMapper.builder
        .withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
        .objectMapper
  }

  @Singleton
  @Provides
  @Named("SnakeCaseObjectMapper")
  def providesSnakeCaseObjectMapper(): ScalaObjectMapper = {
    ScalaObjectMapper.builder
      .withPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
      .objectMapper
  }

  @Singleton
  @Provides
  def providesGoogleOauth(@Named("CamelCaseObjectMapper") scalaObjectMapper: ScalaObjectMapper): GoogleOauthCredentials = {
    scalaObjectMapper.parse[GoogleOauthCredentials](
      Source.fromInputStream(this.getClass.getResourceAsStream("/photo-api-credentials.json")).getLines().mkString
    )
  }

  @Singleton
  @Provides
  def providesGoogleBearerToken(@Named("CamelCaseObjectMapper") scalaObjectMapper: ScalaObjectMapper): GoogleBearerToken = {
    scalaObjectMapper.parse[GoogleBearerToken](
      Source.fromInputStream(this.getClass.getResourceAsStream("/photo-api-refresh-token.json")).getLines().mkString
    )
  }

  @Singleton
  @Provides
  def providesTwitterOauthCredentials(@Named("SnakeCaseObjectMapper") scalaObjectMapper: ScalaObjectMapper): TwitterBearerToken = {
    scalaObjectMapper.parse[TwitterBearerToken](
      Source.fromInputStream(this.getClass.getResourceAsStream("/twitter-bearer-token.json")).getLines().mkString
    )
  }

  @Singleton
  @Provides
  def providesTimer(): Timer = DefaultTimer


  @Singleton
  @Provides
  def providesTwitterAccount(): AppProperties = {
    val properties = new Properties()
    properties.load(this.getClass.getResourceAsStream("/app.properties"))
    AppProperties(properties.getProperty("twitter.handle"), properties.getProperty("twitter.likes.maxFetch").toInt)
  }
}

case class AppProperties(twitterScreenName: String, twitterLikeMaxFetch: Int)

object PhotoFrameServerMain extends PhotoFrameServer

class PhotoFrameServer extends TwitterServer {

  override val modules: Seq[Module] = Seq(PhotoFrameModule)

  override protected def start(): Unit = {
    await(injector.instance[PhotoFrameProcessor].loop())
  }
}