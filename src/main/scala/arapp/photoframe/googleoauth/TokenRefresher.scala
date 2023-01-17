package arapp.photoframe.googleoauth

import java.net.URLEncoder

import com.fasterxml.jackson.databind.{ObjectMapper, PropertyNamingStrategy}
import com.google.inject.Inject
import com.google.inject.name.Named
import com.twitter.finagle.http.{Request, RequestBuilder, Response, Status}
import com.twitter.finagle.{Http, Service, http}
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.inject.Logging
import com.twitter.util.Future


object TokenRefresher {
  // has nothing to do with this class
  def authorizationHeader(accessToken: String): (String, String) = {
    ("Authorization", s"Bearer $accessToken")
  }
}

class TokenRefresher @Inject() (
                                 oauthCredentials: GoogleOauthCredentials,
                                 googleBearerToken: GoogleBearerToken,
                                 @Named("GoogleHttpClient") httpClient: Service[Request, Response],
                                 @Named("CamelCaseObjectMapper") scalaObjectMapper: ScalaObjectMapper,
                                 @Named("SnakeCaseObjectMapper") snakeCaseObjectMapper: ScalaObjectMapper
                               ) extends Logging {

  def encode(param: String) = URLEncoder.encode(param, "UTF-8")

  private def parseAuthResponse(response: String): OauthToken = {
    val json = new ObjectMapper().readTree(response)

    snakeCaseObjectMapper.parse[OauthToken](response)
  }

  def requestNewToken(): Future[OauthToken] = {
    val request = RequestBuilder()
      .url("https://www.googleapis.com/oauth2/v4/token")
      .addFormElement(
        ("client_id" -> oauthCredentials.clientId),
        ("client_secret" -> oauthCredentials.secret),
        ("refresh_token" -> googleBearerToken.refreshToken),
        ("grant_type" -> "refresh_token")
      )
      .buildFormPost()

    httpClient(request).map { response =>
      if (response.status == Status.Ok) {
        info(s"Token refresh returned ${response.contentString}")
        parseAuthResponse(response.contentString)
      } else {
        throw new Exception(s"Token request in auth flow was not successful: status ${response}, ${response.contentString}")
      }
    }
  }
}
