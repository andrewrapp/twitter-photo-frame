package arapp.photoframe.photoapi

import arapp.photoframe.googleoauth.{GoogleBearerToken, TokenState}
import arapp.photoframe.photoapi.domain._
import com.google.inject.Inject
import com.google.inject.name.Named
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.inject.Logging
import com.twitter.io.Buf.ByteArray
import com.twitter.util.Future

class PhotoApi @Inject() (oauthCredentials: GoogleBearerToken,
                          @Named("PhotosApiHttpClient") httpClient: Service[Request, Response],
                          tokenState: TokenState,
                          @Named("CamelCaseObjectMapper") scalaObjectMapper: ScalaObjectMapper) extends Logging {

  def upload(bytes: Array[Byte]): Future[String] = {
    tokenState.token().flatMap { token =>

      val request = Request(Version.Http11, Method.Post, "https://photoslibrary.googleapis.com/v1/uploads")
      request.headerMap.add("X-Goog-Upload-Content-Type", "mime-type")
      request.headerMap.add("X-Goog-Upload-Protocol", "raw")
      request.headerMap.add("Authorization", s"Bearer ${token.accessToken}")
      request.setContentType("application/octet-stream")
      request.content(new ByteArray(bytes, 0, bytes.length))

      info(s"Uploading photo bytes ${request}")

      httpClient(request).map{ response =>
        if (response.status == Status.Ok) {
          // upload token
          response.contentString
        } else {
          throw new Exception(s"Upload failed with status ${response.status}, content ${response.contentString}")
        }
      }
    }
  }

  def createMediaItem(albumId: String, description: String, fileName: String, uploadToken: String): Future[MediaItemsResponse] = {

    tokenState.token().flatMap { token =>

      val request = Request(Version.Http11, Method.Post, "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate")
      request.headerMap.add("Authorization", s"Bearer ${token.accessToken}")
      request.setContentType("application/json")

      info(s"Creating media item for albumId ${albumId} with description ${description}, fileName ${fileName}, uploadToken ${uploadToken}")

      request.setContentString(
        scalaObjectMapper.writeValueAsString(
          MediaItemsRequest(Some(albumId), Seq(MediaItemRequest(description, SimpleMediaItemRequest(fileName, uploadToken))))
        )
      )

      httpClient(request).map{ response =>
        if (response.status == Status.Ok) {
          // upload token
          info(s"Create media item response ${response.contentString}")
          scalaObjectMapper.parse[MediaItemsResponse](response.contentString)
        } else {
          throw new Exception(s"Create media item failed with status ${response.status}, content ${response.contentString}")
        }
      }
    }
  }

  def deleteMediaItem(albumId: String, mediaItemIds: Seq[String]): Future[Unit] = {
    tokenState.token().flatMap { token =>

      val request = Request(Version.Http11, Method.Post, s"https://photoslibrary.googleapis.com/v1/albums/${albumId}:batchRemoveMediaItems")
      request.headerMap.add("Authorization", s"Bearer ${token.accessToken}")
      request.setContentType("application/json")

      info(s"Removing media items ${mediaItemIds} from album")

      request.setContentString(
        scalaObjectMapper.writeValueAsString(
          BatchRemoveMediaItemsRequest(mediaItemIds.map(_.toString))
        )
      )

      httpClient(request).map{ response =>
        if (response.status == Status.Ok) {
          // upload token
          info(s"Batch remove media item response ${response.contentString}")
        } else {
          throw new Exception(s"Batch remove media item failed with status ${response.status}, content ${response.contentString}")
        }
      }
    }
  }

  def listAlbums(): Future[ListAlbums] = {

    tokenState.token().flatMap { token =>
      // this endpoint does not return empty albums and there doesn't seem to be an option to enable that so it that case it will create duplicate albums
      val request = Request(Version.Http11, Method.Get, "https://photoslibrary.googleapis.com/v1/albums")
      request.headerMap.add("Authorization", s"Bearer ${token.accessToken}")

      info(s"Listing albums with request ${request}")

      httpClient(request).map{ response =>
        if (response.status == Status.Ok) {
          info(s"List albums response ${response.contentString}")
          scalaObjectMapper.parse[ListAlbums](response.contentString)
        } else {
          throw new Exception(s"List albums failed with status ${response.status}, content ${response.contentString}")
        }
      }
    }
  }

  def listAlbum(albumId: String, pageSize: Integer = 25): Future[Seq[MediaItemResponse]] = {
    if (pageSize > 100) throw new IllegalArgumentException("Max page size is 100")
    listAlbumPaginate(pageSize, albumId, None, Seq())
  }

  def listAlbumPaginate(pageSize: Integer = 25, albumId: String, nextPage: Option[String], acc: Seq[MediaItemResponse]): Future[Seq[MediaItemResponse]] = {
    tokenState.token().flatMap { token =>
      val request = Request(Version.Http11, Method.Post, "https://photoslibrary.googleapis.com/v1/mediaItems:search")
      request.headerMap.add("Authorization", s"Bearer ${token.accessToken}")
      request.setContentType("application/json")

      request.setContentString(
        scalaObjectMapper.writeValueAsString(
          ListAlbumRequest(pageSize.toString, albumId, pageToken = nextPage)
        )
      )

      info(s"Listing album with page size ${pageSize}, request ${request}")

      httpClient(request).flatMap{ response =>
        if (response.status == Status.Ok) {
          info(s"List album response ${response.contentString}")

          val albumItems: ListAlbumPaginateResponse = scalaObjectMapper.parse[ListAlbumPaginateResponse](response.contentString)

          info(s"Parsed album items ${albumItems}")

          val mediaItems: Seq[MediaItemResponse] = albumItems.mediaItems.getOrElse(Seq())

          if (albumItems.nextPageToken.isDefined) {
            info(s"Requesting next page with token ${albumItems.nextPageToken}")
            listAlbumPaginate(pageSize, albumId, albumItems.nextPageToken, acc ++ mediaItems)
          } else {
            Future(acc ++ mediaItems)
          }
        } else {
          throw new Exception(s"List album failed with status ${response.status}, content ${response.contentString}")
        }
      }
    }
  }

  def findAlbumByTitle(title: String): Future[String] = {
    for {
      albums: ListAlbums <- listAlbums()
      twitterAlbum: Option[Album] = albums.albums.find(_.title == title)
      albumId <- if (twitterAlbum.isDefined) {
        Future(twitterAlbum.get.id)
      } else {
        createMediaAlbum(title).map(_.id)
      }

    } yield (albumId)
  }

  def createMediaAlbum(title: String): Future[AlbumCreateResponse] = {
    tokenState.token().flatMap { token =>

      val request = Request(Version.Http11, Method.Post, "https://photoslibrary.googleapis.com/v1/albums")
      request.headerMap.add("Authorization", s"Bearer ${token.accessToken}")
      request.setContentType("application/json")

      request.setContentString(
        scalaObjectMapper.writeValueAsString(
          AlbumCreateRequest(AlbumRequest(title))
        )
      )

      info(s"Creating album with title ${title}, request ${request}")

      httpClient(request).map{ response =>
        if (response.status == Status.Ok) {
          info(s"Album create response ${response.contentString}")
          // upload token
          scalaObjectMapper.parse[AlbumCreateResponse](response.contentString)
        } else {
          throw new Exception(s"Create media album failed with status ${response.status}, content ${response.contentString}")
        }
      }
    }
  }
}
