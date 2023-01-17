package arapp.photoframe.photoapi.domain

case class MediaItemsRequest(albumId: Option[String], newMediaItems: Seq[MediaItemRequest])
