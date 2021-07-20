package skuber.api.client

import akka.NotUsed
import akka.http.scaladsl.model.{HttpMethods, Uri}
import akka.stream.scaladsl.{Flow, Source}
import play.api.libs.json.Format
import skuber.api.client.impl.KubernetesClientImpl
import skuber.{KListItem, LabelSelector, ListOptions, ListResource, Namespace, ResourceDefinition}

import scala.concurrent.Future

/**
  * @author David O'Riordan
  *
  * A representation of a collection of Kubernetes resources that can be consumed by the
  * application in streaming manner
  */
trait StreamingCollection[O <: KListItem] {
  /**
    * Get a streaming source for the collection
    * @return a source that can be used to stream the resources
    * from the cluster
    */
  def source: Source[O, NotUsed]

  /**
    * Get a streaming source for the collection in every namespace in the cluster.
    * @return a (future) map of namespace name to a Source for the collection in that namespace
    */
  def sourceByNamespace: Future[Map[String, Source[O, NotUsed]]]

  // fluent methods to refine the options used to retrieve the collection from the cluster
  /**
    * Streams only those resources on the cluster that match the label selector
    * @param selector
    */
  def labelled(selector: LabelSelector): StreamingCollection[O]

  /**
    * Streams only those resources matching the given field selector
    */
  def fields(fieldSelector: String): StreamingCollection[O]

  /**
    * Sets a timeout on the Kubernetes API requests  that fetch the resources to be consumed.
    * @param seconds timeout
    */
  def timeout(seconds: Int): StreamingCollection[O]
}

/**
  * This is a specialisation of the StreamingCollection type that retrieves the resources
  * from the server in chunks. This is especially useful for potentially large collections of
  * resources, as this enables them to be both retrieved and consumed in a a manner that
  * minimises memory consumption on both client and Kubernetes API server.
  * @tparam O the resource type
  */
trait ChunkingStreamingCollection[O] extends StreamingCollection[O] {
  /**
    * Set the (maximum) number of resources to be included in each chunk retrieved from the
    * server
    * @param chunkSize
    * @return
    */
  def chunked(chunkSize: Long) : ChunkingStreamingCollection[O]
}