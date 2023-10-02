package skuber.api

import org.apache.pekko.http.scaladsl.model.{HttpCharsets, MediaType}
import play.api.libs.json.Writes

package object patch {

  object CustomMediaTypes {
    val `application/merge-patch+json` = MediaType.applicationWithFixedCharset("merge-patch+json", HttpCharsets.`UTF-8`)
    val `application/strategic-merge-patch+json` = MediaType.applicationWithFixedCharset("strategic-merge-patch+json", HttpCharsets.`UTF-8`)
  }

  object JsonPatchOperation {
    sealed trait Operation {
      def op: String
    }

    trait ValueOperation[T] extends Operation {
      type ValueType = T
      def path: String
      def value: ValueType
      def fmt: Writes[T]
    }

    trait UnaryOperation extends Operation {
      def path: String
    }

    trait DirectionalOperation extends Operation {
      def from: String
      def path: String
    }

    case class Add[T](path: String, value: T)(implicit val fmt: Writes[T]) extends ValueOperation[T] {
      val op = "add"
    }

    case class Remove(path: String) extends UnaryOperation {
      val op = "remove"
    }

    case class Replace[T](path: String, value: T)(implicit val fmt: Writes[T]) extends ValueOperation[T] {
      val op = "replace"
    }

    case class Move(from: String, path: String) extends DirectionalOperation {
      val op = "move"
    }

    case class Copy(from: String, path: String) extends DirectionalOperation {
      val op = "copy"
    }
  }

  trait Patch {
    val strategy: PatchStrategy
  }

  sealed trait PatchStrategy

  sealed trait MergePatchStrategy extends PatchStrategy

  case object StrategicMergePatchStrategy extends MergePatchStrategy

  case object JsonMergePatchStrategy extends MergePatchStrategy

  case object JsonPatchStrategy extends PatchStrategy

  case class JsonPatch(operations: List[JsonPatchOperation.Operation]) extends Patch {
    override val strategy = JsonPatchStrategy
  }

  trait JsonMergePatch extends Patch {
    override val strategy = JsonMergePatchStrategy
  }

  trait StrategicMergePatch extends Patch {
    override val strategy = StrategicMergePatchStrategy
  }

  case class MetadataPatch(labels: Option[Map[String, String]] = Some(Map()),
                           annotations: Option[Map[String, String]] = Some(Map()),
                           override val strategy: MergePatchStrategy = StrategicMergePatchStrategy) extends Patch

}
