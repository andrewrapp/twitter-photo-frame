package arapp.photoframe.googleoauth

import com.google.inject.Inject
import com.google.inject.name.Named
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Future
import org.slf4j.LoggerFactory

case object OauthTokenRequest

class TokenState @Inject() (tokenRefresher: TokenRefresher, oauthCredentials: GoogleBearerToken) {

  val log = LoggerFactory.getLogger(this.getClass)

  // TODO atomic reference
  var future: Option[Future[OauthToken]] = None

  def token(): Future[OauthToken] = {
    processTokenRequest()
  }

  def processTokenRequest(): Future[OauthToken] = {
    val newFuture = future match {
      case Some(future) =>
        future.flatMap { token =>
          if (token.isNearExpiration()) {
            log.debug("Token is near expiration or expired.. refreshing")
            updateToken()
          } else {
            Future(token)
          }
        }
      case None =>
        updateToken()
    }

    newFuture
  }

  def updateToken(): Future[OauthToken] = {
    val token: Future[OauthToken] = tokenRefresher.requestNewToken()

    token.onSuccess(t => future = Some(Future(t)))
  }
}
