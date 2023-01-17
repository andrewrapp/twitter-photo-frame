package arapp.photoframe.photoapi.domain

case class ListAlbums(albums: Seq[Album], nextPageToken: Option[String])
