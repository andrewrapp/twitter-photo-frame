package arapp.photoframe.googleoauth

import java.awt.Desktop
import java.net.{URI, URLEncoder}

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.twitter.finagle.http._
import com.twitter.finagle.{Http, Service, http}
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}

import scala.io.Source
case class OauthAuthContext(redirectScheme: String = "http", redirectHost: String, serverPort: Int, returnPath: String, scopes: Seq[String]) {
  def returnUrl() = s"$redirectScheme://$redirectHost:${serverPort}${returnPath}"
}

object GoogleOauthFlow extends TwitterServer {
  /**
  In the api console select oauth client id and web application
     add localhost and redirect path. google requires ports
     copy client id and secret from the api console registration to this class

     Next, enable the apis that you want to use. Not doing this will cause some pain because
     google will allow you to request access for the apis via this auth flow and for
     some inexplicable reason it will work initially for the first n requests
     but at some point it fail consistently with 403 Forbidden

    revoke access via web ui https://myaccount.google.com/permissions
    */
  def main() {
    //https://developers.google.com/gmail/api/auth/scopes
    val scopes = Seq(
      "https://www.googleapis.com/auth/photoslibrary",
      "https://www.googleapis.com/auth/photoslibrary.sharing"
    )

    val returnPath = "/oauth-return"

    val oauthContext = OauthAuthContext(
      redirectHost = "localhost",
      serverPort = adminPort().getPort,
      returnPath = returnPath,
      scopes = scopes
    )

    val objectMapper: ScalaObjectMapper =
      ScalaObjectMapper.builder
        .withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
        .objectMapper

    val oauthCredentials: GoogleOauthCredentials = objectMapper.parse[GoogleOauthCredentials](
      Source.fromInputStream(this.getClass.getResourceAsStream("/photo-api-credentials.json")).getLines().mkString
    )

    val googleRedirectService = new Service[Request, Response] {
      def apply(request: Request) = {
        val response = Response(request.version, Status.TemporaryRedirect)
        // redirect to google to start auth flow
        response.headerMap.add("Location", googleAuthFlowUrl(oauthCredentials, oauthContext))
        Future.value(response)
      }
    }

    val oauthReturnService = new Service[Request, Response] {
      def apply(request: Request) = {
        val response: Future[Response] = request.params.get("code") match {
          case Some(code) =>
            // get oauth
            val future = requestOauth(oauthCredentials, code, oauthContext)

            future.map{ json =>
              // get the refresh_token and add to resources
              val response = Response(request.version, Status.Ok)
              response.setContentType("application/json")
              response.contentString = json
              response
            }
          case None =>
            val response = Response(request.version, Status.InternalServerError)
            response.contentString = "Error code missing from redirect"
            Future(response)
        }

        // shutdown server after response completes
        response.respond{
          case _ =>
            Thread.sleep(5000)
            println("Shutting down server")
            adminHttpServer.close()
        }

        response
      }
    }

    val googleRedirectPath = "/oauth"

    HttpMuxer.addHandler(googleRedirectPath, googleRedirectService)
    HttpMuxer.addHandler(returnPath, oauthReturnService)

    // admin server defaults to port 9990
    if (Desktop.isDesktopSupported) {
      Desktop.getDesktop.browse(new URI(s"http://localhost:${adminPort().getPort}${googleRedirectPath}"))
    }

    onExit {
      adminHttpServer.close()
    }

    Await.ready(adminHttpServer)
  }

  def googleAuthFlowUrl(oauthCredentials: GoogleOauthCredentials, oauthContext: OauthAuthContext): String = {
    val scopesParam: String = oauthContext.scopes
      .foldLeft("")((str, scope) => str + URLEncoder.encode(scope, "UTF-8") + " ")
      .dropRight(1)

    new StringBuilder("https://accounts.google.com/o/oauth2/v2/auth?")
      .append("client_id=").append(oauthCredentials.clientId)
      .append("&response_type=code")
      .append("&scope=" + scopesParam) // scope is the api permissions we are requesting
      .append("&redirect_uri=" + oauthContext.returnUrl())
      .append("&access_type=offline")
      .append("&approval_prompt=force").toString()
  }

  def requestOauth(oauthCredentials: GoogleOauthCredentials, code: String, oauthContext: OauthAuthContext): Future[String] = {

    val client: Service[http.Request, http.Response] =
      Http.client
        .withTls("www.googleapis.com")
        .newService("www.googleapis.com:443")

    val request = RequestBuilder()
      .url("https://www.googleapis.com/oauth2/v4/token")
      .addFormElement(
        ("code" -> code),
        ("client_id" -> oauthCredentials.clientId),
        ("client_secret" -> oauthCredentials.secret),
        ("redirect_uri" -> oauthContext.returnUrl()),
        ("grant_type" -> "authorization_code")
      )
      .buildFormPost()

    val response: Future[Response] = client(request)

    response.map{ response =>
      if (response.status == Status.Ok) {
        response.contentString
      } else {
        throw new Exception(s"Token request in auth flow was not successful: status ${response}, ${response.contentString}")
      }
    }
  }
}
