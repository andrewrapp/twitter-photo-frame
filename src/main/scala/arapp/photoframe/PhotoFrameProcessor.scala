package arapp.photoframe

import java.io.{BufferedWriter, File, FileWriter}

import arapp.photoframe.photoapi.PhotoApi
import arapp.photoframe.photoapi.domain.MediaItemResponse
import arapp.photoframe.twitterapi.{EmbeddedTweetWithLikeId, Tweet, TwitterApi}
import com.google.inject.Inject
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.util.DefaultTimer
import com.twitter.inject.Logging
import com.twitter.util.{Duration, Future, Timer}
import org.checkerframework.checker.units.qual.s


class PhotoFrameProcessor @Inject() (
                                      twitterApi: TwitterApi,
                                      photoApi: PhotoApi,
                                      puppeteer: Puppeteer,
                                      appProperties: AppProperties,
                                      timer: Timer
                                    ) extends Logging {

  implicit val defaultTimer = timer

  def processEmbeddedTweets(embeddedTweets: Seq[EmbeddedTweetWithLikeId], albumId: String): Future[Unit] = {
    info(s"Processing likes ${embeddedTweets}")

    puppeteer.processBatch(embeddedTweets)

    Future.traverseSequentially(embeddedTweets) { embeddedTweetWithLikeId: EmbeddedTweetWithLikeId =>
      for {
        tweetBytes <- Future(puppeteer.readScreenshotBytes(embeddedTweetWithLikeId))
        uploadToken <- photoApi.upload(tweetBytes)
        _ <- photoApi.createMediaItem(albumId, embeddedTweetWithLikeId.likeId.toString, embeddedTweetWithLikeId.likeId.toString, uploadToken)
        _ <-  Future.sleep(200.milliseconds) // slow it down
      } yield()
    }.unit
  }

  def compliance(albumContents: Seq[MediaItemResponse], tweetLikes: Seq[Tweet]): Seq[String] = {
//    val tweetImagesOnDisk: Seq[String] = new File(".").list().filter(_.endsWith(".jpg")).map(_.split("\\.").head).toSeq
    val photoAlbumTweets: Seq[String] = albumContents.map(_.filename)
    val apiLikes = tweetLikes.map(_.id.toString)

    info(s"Tweets from api ${apiLikes}")
    info(s"Tweets in album ${photoAlbumTweets}")

    photoAlbumTweets.diff(apiLikes)
  }

  // TODO flag
  val twitterAlbumTitle = "Twitter Photo Frame"

  def process(): Future[Unit] = {
    // make screen name a flag
    def fetchEmbeddedTweets(likes: Seq[Tweet], albumId: String, existing: Seq[MediaItemResponse]): Future[Seq[EmbeddedTweetWithLikeId]] = {
      Future.traverseSequentially(likes) { (like: Tweet) =>
        for {
          tweet <- if (existing.exists(_.filename == like.id.toString)) {
           info(s"Like already exists in photo album ${like}")
            Future(Seq())
          } else {
            info(s"Like tweet id ${like.id} does not exist in google.. creating")
            twitterApi.embeddedTweet(like)
          }
          _ <-  Future.sleep(200.milliseconds) // slow it down
        } yield(tweet.map(t => EmbeddedTweetWithLikeId(like.id, t)))
      }.map(_.flatten)
    }

    for {
      albumId <- photoApi.findAlbumByTitle(twitterAlbumTitle)
      existingAlbumItems: Seq[MediaItemResponse] <- photoApi.listAlbum(albumId)
      likes: Seq[Tweet] <- twitterApi.likesAll(appProperties.twitterScreenName, resultsPerPage = Some(200))
      bw = new BufferedWriter(new FileWriter(s"${appProperties.twitterScreenName}-likes"))
      // for debugging
      _ = likes.foreach{l =>
        //bw.write(s"\ntweet ${l}, url https://twitter.com/${l.user.screenName}/status/${l.id}")
        bw.write(s"\n${l.createdAt}, ${l.text}, url https://twitter.com/${l.user.screenName}/status/${l.id}")
      }
      _ = bw.close()
      tweetDeletes = compliance(existingAlbumItems, likes)
      _ = info(s"Compliance tweet deletes ${tweetDeletes}")
      _ <- Future.traverseSequentially(tweetDeletes) { tweetId: String =>
        // fix this
        val mediaIds: Seq[String] = existingAlbumItems.filter(_.filename == tweetId).map(_.id)

        info(s"Removing tweet ids from album that no longer exist in twitter: ${mediaIds}")

        for {
          _ <- photoApi.deleteMediaItem(albumId, mediaIds)
          _ <-  Future.sleep(200.milliseconds) // slow it down
        } yield()
      }
      embeddedTweets <- fetchEmbeddedTweets(likes, albumId, existingAlbumItems)

      _ <- if (embeddedTweets.nonEmpty) {
        processEmbeddedTweets(embeddedTweets, albumId)
      } else {
        Future.Unit
      }
    } yield ()
  }

  def loop(): Future[Unit] = {
    implicit val timer = DefaultTimer

    process().flatMap { _ =>
      info("Sleeping...")
      Future.sleep(Duration.fromMinutes(15)).before(loop())
    }.rescue {
      case t =>
        error("Process failed ", t)
        Future.sleep(Duration.fromMinutes(1)).before(loop())
    }
  }
}