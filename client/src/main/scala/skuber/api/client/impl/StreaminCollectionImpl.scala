package skuber.api.client.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import skuber.{KListItem, LabelSelector, ListOptions, ListResource, ResourceDefinition}
import skuber.api.client.{ChunkingStreamingCollection, StreamingCollection}

import scala.concurrent.Future

/**
  * @author David O'Riordan
  */
class ChunkingStreamingCollectionImpl[O <: KListItem](
  client: KubernetesClientImpl,
  listOptions: ListOptions
)(implicit rd: ResourceDefinition[ListResource[O]], format: Format[ListResource[O]]) extends  ChunkingStreamingCollection[O]
{
  implicit val executor = client.executionContext

  val chunkSize = listOptions.limit.orElse(Some(25))

  // Command to be used for each stage of the retrieval of the collection -
  // either get next chunk from the API server or finish (when no more items left)

  sealed trait Command
  case class RetrieveNextChunk(continueToken: Option[String]) extends Command
  case object Finish extends Command

  private val getNextChunk: Option[String] => Future[ListResource[O]] = { continueToken =>
    val nextChunkOptions = listOptions.copy(continue = continueToken, limit = chunkSize)
    client.listWithOptions[ListResource[O]](nextChunkOptions)
  }

  private def chunkingCollectionSource: Source[O, NotUsed] = Source
      .unfoldAsync[Command, ListResource[O]](RetrieveNextChunk(None)) {
        case RetrieveNextChunk(continueToken) =>
          getNextChunk(continueToken).map { result: ListResource[O] =>
            val nextCommand = result.metadata.map(_.continue) match {
              case Some(token) => RetrieveNextChunk(token) // there are more items to retrieve
              case None => Finish // all items retrieved
            }
            Some(nextCommand -> result)
          }
        case Finish => Future.successful(None) // done
      }
      .flatMapConcat { chunk =>
        Source(chunk.items) // stream out the resources in each chunk
      }

  override def source: Source[O, NotUsed] = chunkingCollectionSource

  override def sourceByNamespace: Future[Map[String, Source[O, NotUsed]]] = {
    val namespacesFut: Future[List[String]] = client.getNamespaceNames
    namespacesFut.map { namespaces =>
      namespaces.map { namespace =>
        val substream = new ChunkingStreamingCollectionImpl[O](client.usingNamespace(namespace), listOptions)
        namespace -> substream.source
      }.toMap
    }
  }

  override def chunked(chunkSize: Long): ChunkingStreamingCollection[O] = {
    new ChunkingStreamingCollectionImpl[O](client, listOptions.copy(limit = Some(chunkSize)))
  }

  override def fields(fieldSelector: String): StreamingCollection[O] = {
    new ChunkingStreamingCollectionImpl[O](client, listOptions.copy(fieldSelector = Some(fieldSelector)))
  }

  override def labelled(selector: LabelSelector): StreamingCollection[O] = {
    new ChunkingStreamingCollectionImpl[O](client, listOptions.copy(labelSelector = Some(selector)))
  }

  override def timeout(seconds: Int): StreamingCollection[O] = {
    new ChunkingStreamingCollectionImpl[O](client, listOptions.copy(timeoutSeconds = Some(seconds)))
  }
}
