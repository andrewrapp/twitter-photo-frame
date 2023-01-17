package arapp.photoframe.photoapi.domain

case class MediaItemResponse(
                              id: String,
                              description: Option[String],
                              productUrl: String,
                              mimeType: String,
                              mediaMetadata: MediaMetadataResponse,
                              // ugh, inconsistent: they use fileName elsewhere
                              filename: String
                            )
