package arapp.photoframe.photoapi.domain

case class ListAlbumPaginateResponse(mediaItems: Option[Seq[MediaItemResponse]], nextPageToken: Option[String])
case class ListAlbumResponse(mediaItems: Seq[MediaItemResponse])
