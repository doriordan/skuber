package skuber.operator.crd

import play.api.libs.json.*
import scala.deriving.Mirror
import scala.compiletime.*

/**
 * Runtime helper for constructing Play Json OFormat instances for case classes.
 * Used by the @customResource macro to avoid calling Json.format[T] which
 * has ScopeException issues when invoked from within macro annotations.
 *
 * Uses Mirror.ProductOf for type-safe construction/deconstruction.
 */
object FormatHelper:

  /**
   * Derive an OFormat for a product type T given its field names.
   * Field Reads/Writes are resolved via compile-time summoning.
   * Mirror is passed explicitly via using clause.
   */
  inline def deriveFormat[T](fieldNames: List[String])(using m: Mirror.ProductOf[T]): OFormat[T] =
    val reads = summonReads[m.MirroredElemTypes]
    val writes = summonWrites[m.MirroredElemTypes]
    createFormatFromMirror[T](fieldNames, reads, writes, m)

  private def createFormatFromMirror[T](
    fieldNames: List[String],
    fieldReads: List[Reads[?]],
    fieldWrites: List[Writes[?]],
    mirror: Mirror.ProductOf[T]
  ): OFormat[T] = OFormat(
    Reads[T] { json =>
      try
        val values = fieldNames.zip(fieldReads).map { (name, reader) =>
          (json \ name).as(reader.asInstanceOf[Reads[Any]])
        }
        val product = mirror.fromProduct(Tuple.fromArray(values.toArray))
        JsSuccess(product)
      catch
        case e: JsResultException => JsError(e.errors)
        case e: Exception => JsError(e.getMessage)
    },
    OWrites[T] { o =>
      val values = o.asInstanceOf[Product].productIterator.toList
      val pairs = fieldNames.zip(fieldWrites).zip(values).map { case ((name, writer), value) =>
        name -> writer.asInstanceOf[Writes[Any]].writes(value)
      }
      JsObject(pairs)
    }
  )

  // Compile-time summoning of Reads instances for tuple types
  inline def summonReads[T <: Tuple]: List[Reads[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t) => summonInline[Reads[h]] :: summonReads[t]

  // Compile-time summoning of Writes instances for tuple types
  inline def summonWrites[T <: Tuple]: List[Writes[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t) => summonInline[Writes[h]] :: summonWrites[t]
