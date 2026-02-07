package skuber.operator.crd

import scala.annotation.experimental
import scala.quoted.*
import play.api.libs.json.{JsObject, JsString, JsArray, JsBoolean, JsValue, Json}

/**
 * Generates OpenAPI v3 schema from Scala case class structures at compile time.
 *
 * Type mappings:
 * | Scala Type | OpenAPI Schema |
 * |------------|----------------|
 * | Int | {"type": "integer", "format": "int32"} |
 * | Long | {"type": "integer", "format": "int64"} |
 * | Double | {"type": "number", "format": "double"} |
 * | Float | {"type": "number", "format": "float"} |
 * | Boolean | {"type": "boolean"} |
 * | String | {"type": "string"} |
 * | Option[T] | schema of T (field not in required) |
 * | List[T] | {"type": "array", "items": <T schema>} |
 * | Seq[T] | {"type": "array", "items": <T schema>} |
 * | Map[String,T] | {"type": "object", "additionalProperties": <T schema>} |
 * | case class | {"type": "object", "properties": {...}, "required": [...]} |
 */
object OpenApiSchemaGenerator:

  /**
   * Generate OpenAPI schema for a case class type at compile time.
   */
  inline def schemaFor[T]: JsObject = ${ schemaForImpl[T] }

  private def schemaForImpl[T: Type](using Quotes): Expr[JsObject] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    generateSchema(tpe)

  private def generateSchema(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[JsObject] =
    import quotes.reflect.*

    tpe.dealias match
      // Primitive types
      case t if t =:= TypeRepr.of[Int] =>
        '{ JsObject(Map("type" -> JsString("integer"), "format" -> JsString("int32"))) }

      case t if t =:= TypeRepr.of[Long] =>
        '{ JsObject(Map("type" -> JsString("integer"), "format" -> JsString("int64"))) }

      case t if t =:= TypeRepr.of[Double] =>
        '{ JsObject(Map("type" -> JsString("number"), "format" -> JsString("double"))) }

      case t if t =:= TypeRepr.of[Float] =>
        '{ JsObject(Map("type" -> JsString("number"), "format" -> JsString("float"))) }

      case t if t =:= TypeRepr.of[Boolean] =>
        '{ JsObject(Map("type" -> JsString("boolean"))) }

      case t if t =:= TypeRepr.of[String] =>
        '{ JsObject(Map("type" -> JsString("string"))) }

      // Option[T] - unwrap to get inner type schema
      case AppliedType(tycon, List(inner)) if tycon.typeSymbol == TypeRepr.of[Option[_]].typeSymbol =>
        generateSchema(inner)

      // List[T] or Seq[T]
      case AppliedType(tycon, List(inner)) if tycon.typeSymbol == TypeRepr.of[List[_]].typeSymbol ||
                                               tycon.typeSymbol == TypeRepr.of[Seq[_]].typeSymbol =>
        val itemsSchema = generateSchema(inner)
        '{ JsObject(Map("type" -> JsString("array"), "items" -> $itemsSchema)) }

      // Map[String, T]
      case AppliedType(tycon, List(keyType, valueType)) if tycon.typeSymbol == TypeRepr.of[Map[_, _]].typeSymbol =>
        if !(keyType =:= TypeRepr.of[String]) then
          report.errorAndAbort(s"Map keys must be String for OpenAPI schema, got: ${keyType.show}")
        val valueSchema = generateSchema(valueType)
        '{ JsObject(Map("type" -> JsString("object"), "additionalProperties" -> $valueSchema)) }

      // Case class
      case t if t.typeSymbol.flags.is(Flags.Case) =>
        generateCaseClassSchema(t)

      case other =>
        report.warning(s"Unknown type for OpenAPI schema: ${other.show}, using generic object")
        '{ JsObject(Map("type" -> JsString("object"))) }

  private def generateCaseClassSchema(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[JsObject] =
    import quotes.reflect.*

    val sym = tpe.typeSymbol
    val fields = sym.caseFields

    case class FieldInfo(name: String, schema: Expr[JsObject], isRequired: Boolean)

    val fieldInfos = fields.map { field =>
      val fieldName = field.name
      val fieldType = tpe.memberType(field)

      // Check if field has a default value
      val hasDefault = sym.companionClass.declaredMethod(s"$$lessinit$$greater$$default$$${fields.indexOf(field) + 1}").nonEmpty

      // Check if field is Option
      val isOption = fieldType match
        case AppliedType(tycon, _) => tycon.typeSymbol == TypeRepr.of[Option[_]].typeSymbol
        case _ => false

      val isRequired = !isOption && !hasDefault
      val schema = generateSchema(fieldType)

      FieldInfo(fieldName, schema, isRequired)
    }

    // Build properties object
    val propertiesExpr: Expr[Map[String, JsValue]] = {
      val pairs = fieldInfos.map { fi =>
        val nameExpr = Expr(fi.name)
        val schemaExpr = fi.schema
        '{ ($nameExpr, $schemaExpr: JsValue) }
      }
      val pairsExpr = Expr.ofList(pairs)
      '{ $pairsExpr.toMap }
    }

    // Build required array
    val requiredFields = fieldInfos.filter(_.isRequired).map(_.name)
    val requiredExpr = Expr(requiredFields)

    '{
      val props = JsObject($propertiesExpr)
      val required = $requiredExpr
      if required.isEmpty then
        JsObject(Map("type" -> JsString("object"), "properties" -> props))
      else
        JsObject(Map(
          "type" -> JsString("object"),
          "properties" -> props,
          "required" -> JsArray(required.map(JsString(_)))
        ))
    }

  /**
   * Generate a complete CRD schema with spec and status sections.
   */
  inline def crdSchemaFor[Spec, Status]: JsObject = ${ crdSchemaForImpl[Spec, Status] }

  private def crdSchemaForImpl[Spec: Type, Status: Type](using Quotes): Expr[JsObject] =
    import quotes.reflect.*

    val specSchema = generateSchema(TypeRepr.of[Spec])
    val statusSchema = generateSchema(TypeRepr.of[Status])

    '{
      JsObject(Map(
        "type" -> JsString("object"),
        "properties" -> JsObject(Map(
          "apiVersion" -> JsObject(Map("type" -> JsString("string"))),
          "kind" -> JsObject(Map("type" -> JsString("string"))),
          "metadata" -> JsObject(Map("type" -> JsString("object"))),
          "spec" -> $specSchema,
          "status" -> $statusSchema
        ))
      ))
    }
