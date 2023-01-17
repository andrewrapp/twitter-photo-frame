package arapp.photoframe

import arapp.photoframe.twitterapi.{Tweet}
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.twitter.finatra.jackson.ScalaObjectMapper

import scala.io.Source

object ParseTweets extends App {

  val scalaObjectMapper = ScalaObjectMapper.builder
    .withPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .objectMapper


  val likes = Source.fromInputStream(this.getClass.getResourceAsStream("/likes.json")).getLines().mkString

  val t = scalaObjectMapper.parse[Seq[Tweet]](likes)

  println(t)

}
