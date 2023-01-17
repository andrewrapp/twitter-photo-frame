package arapp.photoframe.googleoauth

import org.joda.time.{DateTime, DateTimeZone}

// Example:
//{
//"access_token": "ya29.a0Ae4lvC2rvGtAz0aaltNzHJ_tSolck1dELUtW5v3p8TjEBo65fXlCV7fHU8Z5T2CNv07JRTKcpOKS4tvUpNBMLp9JHTL28u8l8vaTXOYexolvguKgveH3xeg7REDe8_DKxdRz9rzcQAlIVfzadWeadPBkXAeM-G_WqB9E",
//"expires_in": 3599,
//"scope": "https://www.googleapis.com/auth/photoslibrary.sharing https://www.googleapis.com/auth/photoslibrary",
//"token_type": "Bearer"
//}

case class OauthToken(
                       accessToken: String,
                       expiresIn: Int,
                       scope: String,
                       tokenType: String
                     ) {

  val expiresAt = new DateTime().withZone(DateTimeZone.UTC).plusSeconds(expiresIn)

  def isExpired() = {
    expiresAt.isAfterNow
  }

  def now() = {
    new DateTime().withZone(DateTimeZone.UTC)
  }

  def isNearExpiration() = {
    new DateTime().withZone(DateTimeZone.UTC).isAfter(expiresAt.minusSeconds(300))
  }
}