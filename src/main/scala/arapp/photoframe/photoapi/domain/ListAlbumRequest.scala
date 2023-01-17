package arapp.photoframe.photoapi.domain

case class ListAlbumRequest(pageSize: String, albumId: String, pageToken: Option[String])
