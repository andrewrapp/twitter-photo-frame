package arapp.photoframe

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.{Files, Paths}

import arapp.photoframe.twitterapi.EmbeddedTweetWithLikeId
import com.google.inject.Inject
import com.twitter.inject.Logging

import scala.sys.process._

class Puppeteer @Inject()() extends Logging {

  def readScreenshotBytes(embeddedTweetWithLikeId: EmbeddedTweetWithLikeId): Array[Byte] = {
    val file = new File(s"./scripts/${embeddedTweetWithLikeId.likeId}.html")
    file.delete()

    val imageFile = new File(s"./node-puppeteer/${embeddedTweetWithLikeId.likeId}.jpg")

    val bytes = Files.readAllBytes(Paths.get(imageFile.getAbsolutePath))

    imageFile.delete()

    bytes
  }

  def processBatch(embeddedTweets: Seq[EmbeddedTweetWithLikeId]): Unit = {

    val embeddedDir = new File("embedded-out")

    if (!embeddedDir.exists()) {
      info(s"Creating dir ${embeddedDir} for embedded tweet html")

      if (!embeddedDir.mkdir()) {
        throw new Exception(s"Failed to create director ${embeddedDir}")
      }
    }

    if (embeddedDir.listFiles().size > 0) {
      info(s"Embedded directory contains ${embeddedDir.listFiles().size}, expected zero")
    }

    embeddedTweets.map { embeddedTweet =>
      val file = new File(embeddedDir, s"/${embeddedTweet.likeId}.html")

      info(s"Writing embedded tweet ${embeddedTweet} to ${embeddedDir.getAbsolutePath}")

      val bw = new BufferedWriter(new FileWriter(file))
      bw.write(embeddedTweet.embeddedTweet.html)
      bw.close()
    }

    val script: String = "./scripts/puppeteer-screenshot.sh"

    // run node puppeteer app. pass in input dir and aspect ratio
    val command = s"${script} ${embeddedDir.getAbsolutePath} 16/9"

    info(s"Executing ${command}")

    val result = command !

    if (result != 0) {
      throw new Exception("Failed to create screenshot")
    }


    embeddedTweets.foreach { embeddedTweet =>
      val file = new File(embeddedDir, s"./scripts/${embeddedTweet.likeId}.html")
      file.delete()
    }
  }
}
