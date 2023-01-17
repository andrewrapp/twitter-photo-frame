package arapp.photoframe.twitterapi

import com.google.inject.Inject
import com.google.inject.name.Named
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.inject.Logging
import com.twitter.util.Future


case class User(id: Long, name: String, screenName: String, followersCount: Integer, friendsCount: Integer, createdAt: String, favouritesCount: Integer, verified: Boolean, statusesCount: Integer)
// abbrevated representation of tweet payload
case class Tweet(id: Long, createdAt: String, truncated: Boolean, text: String, user: User, inReplyToStatusId: Option[Long], retweetCount: Integer, favoriteCount: Integer)

case class EmbeddedTweetWithLikeId(likeId: Long, embeddedTweet: EmbeddedTweet)

case class EmbeddedTweet(
                    url: String,
                    authorName: String,
                    authorUrl: String,
                    html: String,
                    width: Option[Integer],
                    height: Option[Integer],
                    `type`: String,
                    cacheAge: Long,
                    providerName: String,
                    providerUrl: String,
                    version: String
                  )

class TwitterApi @Inject()(
                            @Named("TwitterApiHttpClient") twitterApiClient: Service[Request, Response],
                            @Named("TwitterPublishApiHttpClient") twitterPublishApClient: Service[Request, Response],
                            twitterBearerToken: TwitterBearerToken,
                            @Named("SnakeCaseObjectMapper") scalaObjectMapper: ScalaObjectMapper
                          ) extends Logging {
  def likes(screenName: String, count: Option[Integer] = Some(100), sinceId: Option[Long] = None, maxId: Option[Long] = None): Future[Seq[Tweet]] = {

    val args = Map("screen_name" -> screenName) ++
      count.map(count => Map("count" -> count.toString)).getOrElse(Map.empty) ++
      sinceId.map(sinceId => Map("since_id" -> sinceId.toString)).getOrElse(Map.empty) ++
      maxId.map(maxId => Map("max_id" -> maxId.toString)).getOrElse(Map.empty)

    val request: Request = RequestBuilder()
      .addHeader("Authorization", s"Bearer ${twitterBearerToken.accessToken}")
      .url(Request.queryString("https://api.twitter.com/1.1/favorites/list.json", args))
      .buildGet()

    info(s"Requesting liked tweets ${request}")

    twitterApiClient(request).map { response =>
      if (response.status == Status.Ok) {
        info(s"Likes request returned ${response.contentString}")
        scalaObjectMapper.parse[Seq[Tweet]](response.contentString)
      } else {
        throw new Exception(s"Request likes failed with ${response.status}, headers ${response.headerMap}, content ${response.contentString}")
      }
    }
  }

  /**
    * Paginate through all likes until an empty set is returned
    *
    * @param screenName
    * @return
    */
  def likesAll(screenName: String, resultsPerPage: Option[Integer] = Some(100), totalResults: Option[Integer] = None): Future[Seq[Tweet]] = {

    for {
      results <- resultsPerPage
      max <- totalResults
    } yield {
      if (max < results) {
        throw new IllegalArgumentException("maxResults cannot be less than resultsPerPage")
      }
    }

    def paginate(acc: Seq[Tweet], maxId: Option[Long]): Future[Seq[Tweet]] = {
      likes(screenName, count = resultsPerPage, maxId = maxId).flatMap{ (likesPage: Seq[Tweet]) =>
        info(s"Received ${likesPage.size} tweets, ${likesPage.map(_.id)}")

        if (likesPage.size > 0 && acc.size < totalResults.map(_.toInt).getOrElse(Integer.MAX_VALUE)) {
          // subtract one since maxId is inclusive
          val lastId = likesPage.takeRight(1).head.id - 1

          info(s"Requesting next page of likes with max id ${lastId}")
          paginate(acc ++ likesPage, maxId = Some(lastId))
        } else {
          val likes = acc ++ likesPage
          info(s"No more likes remaining or reached maxResults. Returning ${likes.size} likes")
          Future(acc ++ likesPage)
        }
      }
    }

    paginate(Seq(), None)
  }

  // Send request to no-auth oembeed api https://developer.twitter.com/en/docs/twitter-api/v1/tweets/post-and-engage/api-reference/get-statuses-oembed
  def embeddedTweet(tweet: Tweet): Future[Seq[EmbeddedTweet]] = {
    // no auth required for this endpoint
    val arg = Map("url" -> s"https://twitter.com/${tweet.user.screenName}/status/${tweet.id}", "align" -> "center")

    val request: Request = RequestBuilder()
      .url(Request.queryString("https://publish.twitter.com/oembed", arg))
      .buildGet()

    info(s"Requesting embedded tweet ${request}")

    twitterPublishApClient(request).map { response =>
      if (response.status == Status.Ok) {
        info(s"Parsing embedded tweet ${response.contentString}")
        Seq(scalaObjectMapper.parse[EmbeddedTweet](response.contentString))
      } else {
        throw new Exception(s"Embedded tweet request failed with ${response.status}, content ${response.contentString}")
      }
    }
  }
}