package skuber.operator.crd

import scala.annotation.experimental
import scala.quoted.*
import play.api.libs.json.{Format, OFormat}
import scala.deriving.Mirror

@experimental
object CustomResourceMacro:

  def transform(using Quotes)(
    definition: quotes.reflect.Definition,
    group: String,
    version: String,
    kind: String,
    plural: String,
    singular: String,
    shortNames: List[String],
    scope: Scope,
    statusSubresource: Boolean,
    scaleSubresource: Boolean
  ): List[quotes.reflect.Definition] =
    import quotes.reflect.*

    definition match
      case obj @ ClassDef(objName, constr, parents, self, body) if obj.symbol.flags.is(Flags.Module) =>
        val specClassOpt = body.collectFirst {
          case cd @ ClassDef("Spec", _, _, _, _) if cd.symbol.flags.is(Flags.Case) => cd
        }

        if specClassOpt.isEmpty then
          report.errorAndAbort(s"@customResource requires a case class named 'Spec' inside object $objName")

        val specClass = specClassOpt.get

        val statusClassOpt = body.collectFirst {
          case cd @ ClassDef("Status", _, _, _, _) if cd.symbol.flags.is(Flags.Case) => cd
        }

        val hasStatusClass = statusClassOpt.isDefined

        // Validate the object extends the correct trait
        val parentTypes = obj.symbol.typeRef.baseClasses.map(_.fullName)
        if hasStatusClass then
          if !parentTypes.contains("skuber.operator.crd.CustomResourceDef") then
            report.errorAndAbort(
              s"Object $objName has a Status class and must extend CustomResourceDef[${objName}.Spec, ${objName}.Status]"
            )
        else
          if !parentTypes.contains("skuber.operator.crd.CustomResourceSpecDef") then
            report.errorAndAbort(
              s"Object $objName must extend CustomResourceSpecDef[${objName}.Spec]"
            )

        val kindLower = kind.toLowerCase
        val singularName = if (singular.nonEmpty) singular else kindLower
        val pluralName = if (plural.nonEmpty) plural else s"${singularName}s"

        val scopeValue = scope.toString
        assert(scopeValue.equals("Namespaced") || scopeValue.equals("Cluster"))

        val objSym = obj.symbol

        // Check which formatters the user has already overridden in the object body
        val hasUserSpecFormat = body.exists {
          case vd: ValDef => vd.name == "specFormat"
          case _ => false
        }
        val hasUserStatusFormat = body.exists {
          case vd: ValDef => vd.name == "statusFormat"
          case _ => false
        }

        // Find ALL case classes in the body (for nested type support)
        val allCaseClasses = body.collect {
          case cd @ ClassDef(name, _, _, _, _) if cd.symbol.flags.is(Flags.Case) => cd
        }

        val newMembers = generateMembers(
          objSym, kind, group, version, pluralName, singularName, shortNames, scopeValue,
          hasStatusClass, statusSubresource, scaleSubresource,
          hasUserSpecFormat, hasUserStatusFormat,
          allCaseClasses
        )

        val newBody = body ++ newMembers
        ClassDef.copy(obj)(objName, constr, parents, self, newBody) :: Nil

      case other =>
        report.errorAndAbort("@customResource can only be applied to an object definition")

  private def generateMembers(using Quotes)(
    objSym: quotes.reflect.Symbol,
    kind: String,
    group: String,
    version: String,
    plural: String,
    singular: String,
    shortNames: List[String],
    scope: String,
    hasStatusClass: Boolean,
    enableStatusSubresource: Boolean,
    enableScaleSubresource: Boolean,
    hasUserSpecFormat: Boolean,
    hasUserStatusFormat: Boolean,
    allCaseClasses: List[quotes.reflect.ClassDef]
  ): List[quotes.reflect.Statement] =
    import quotes.reflect.*

    val members = List.newBuilder[Statement]
    val caseClassSymbols = allCaseClasses.map(_.symbol).toSet

    // ================================================================
    // STEP 1: Topologically sort case classes by dependencies
    // ================================================================

    def extractCaseClassDeps(tpe: TypeRepr): Set[Symbol] =
      val directSym = tpe.typeSymbol
      val fromDirect = if caseClassSymbols.contains(directSym) then Set(directSym) else Set.empty[Symbol]
      val fromArgs = tpe match
        case AppliedType(_, args) => args.flatMap(extractCaseClassDeps).toSet
        case _ => Set.empty[Symbol]
      fromDirect ++ fromArgs

    def getDependencies(cc: ClassDef): Set[Symbol] =
      cc.symbol.caseFields.flatMap(field => extractCaseClassDeps(field.info)).toSet

    def topoSort(remaining: List[ClassDef], sorted: List[ClassDef]): List[ClassDef] =
      if remaining.isEmpty then sorted.reverse
      else
        val alreadySorted = sorted.map(_.symbol).toSet
        val (ready, notReady) = remaining.partition(cc => getDependencies(cc).forall(alreadySorted.contains))
        if ready.isEmpty && notReady.nonEmpty then
          report.errorAndAbort(s"Circular dependency detected among: ${notReady.map(_.name).mkString(", ")}")
        topoSort(notReady, ready.reverse ++ sorted)

    val sortedCaseClasses = topoSort(allCaseClasses, Nil)

    // ================================================================
    // STEP 2: Generate formats for all case classes in order
    // ================================================================

    var generatedFormatSymbols: Map[Symbol, Symbol] = Map.empty

    // Check if a type contains any nested case class (for container detection)
    def containsNestedCaseClass(tpe: TypeRepr): Boolean =
      if caseClassSymbols.contains(tpe.typeSymbol) then true
      else tpe match
        case AppliedType(_, args) => args.exists(containsNestedCaseClass)
        case _ => false

    // Build format expression for a field type using quotes for type safety
    def buildFormatExpr(fieldType: TypeRepr, fieldName: String, ccName: String): Expr[Format[?]] =
      val fieldTypeSym = fieldType.typeSymbol

      // Scala 3 enum
      if fieldTypeSym.flags.is(Flags.Enum) then
        buildEnumFormat(fieldType)

      // Direct nested case class
      else if caseClassSymbols.contains(fieldTypeSym) then
        generatedFormatSymbols.get(fieldTypeSym) match
          case Some(nestedFormatSym) =>
            fieldType.asType match
              case '[t] => Ref(nestedFormatSym).asExprOf[Format[t]]
          case None =>
            report.errorAndAbort(s"Format for ${fieldTypeSym.name} not yet generated - dependency order error")

      // Container types - handle ALL of them, not just those with nested case classes
      // This is because Play JSON doesn't have simple Format[Option[T]] etc instances
      else
        fieldType match
          case AppliedType(tycon, List(innerType)) =>
            tycon.typeSymbol.fullName match
              case "scala.collection.immutable.List" =>
                buildListFormat(innerType, fieldName, ccName)
              case "scala.Option" =>
                buildOptionFormat(innerType, fieldName, ccName)
              case "scala.collection.immutable.Set" =>
                buildSetFormat(innerType, fieldName, ccName)
              case "scala.collection.immutable.Seq" | "scala.collection.Seq" =>
                buildSeqFormat(innerType, fieldName, ccName)
              case "scala.collection.immutable.Vector" =>
                buildVectorFormat(innerType, fieldName, ccName)
              case _ =>
                searchImplicitFormat(fieldType, fieldName, ccName)

          case AppliedType(tycon, List(keyType, valueType)) =>
            tycon.typeSymbol.fullName match
              case "scala.collection.immutable.Map" | "scala.collection.Map" | "scala.Predef.Map" =>
                buildMapFormat(valueType, fieldName, ccName)
              case _ =>
                searchImplicitFormat(fieldType, fieldName, ccName)

          case _ =>
            // Check for common Kubernetes types first, then fall back to implicit search
            buildCommonTypeFormat(fieldType).getOrElse(
              searchImplicitFormat(fieldType, fieldName, ccName)
            )

    def buildEnumFormat(enumType: TypeRepr): Expr[Format[?]] =
      val enumSym = enumType.typeSymbol
      val companion = enumSym.companionModule
      if companion == Symbol.noSymbol then
        report.errorAndAbort(s"No companion object found for enum ${enumSym.fullName}")

      val valuesTerm = Select.unique(Ref(companion), "values")

      enumType.asType match
        case '[t] =>
          val valuesExpr = valuesTerm.asExprOf[Array[t]]
          '{ EnumFormats.enumFormat[t]($valuesExpr) }

    // Container format builders using quotes for type safety
    def buildListFormat(innerType: TypeRepr, fieldName: String, ccName: String): Expr[Format[?]] =
      val innerFormat = buildFormatExpr(innerType, fieldName, ccName)
      innerType.asType match
        case '[t] =>
          val inner = innerFormat.asExprOf[Format[t]]
          '{ ContainerFormats.listFormat[t]($inner) }

    def buildOptionFormat(innerType: TypeRepr, fieldName: String, ccName: String): Expr[Format[?]] =
      val innerFormat = buildFormatExpr(innerType, fieldName, ccName)
      innerType.asType match
        case '[t] =>
          val inner = innerFormat.asExprOf[Format[t]]
          '{ ContainerFormats.optionFormat[t]($inner) }

    def buildSetFormat(innerType: TypeRepr, fieldName: String, ccName: String): Expr[Format[?]] =
      val innerFormat = buildFormatExpr(innerType, fieldName, ccName)
      innerType.asType match
        case '[t] =>
          val inner = innerFormat.asExprOf[Format[t]]
          '{ ContainerFormats.setFormat[t]($inner) }

    def buildSeqFormat(innerType: TypeRepr, fieldName: String, ccName: String): Expr[Format[?]] =
      val innerFormat = buildFormatExpr(innerType, fieldName, ccName)
      innerType.asType match
        case '[t] =>
          val inner = innerFormat.asExprOf[Format[t]]
          '{ ContainerFormats.seqFormat[t]($inner) }

    def buildVectorFormat(innerType: TypeRepr, fieldName: String, ccName: String): Expr[Format[?]] =
      val innerFormat = buildFormatExpr(innerType, fieldName, ccName)
      innerType.asType match
        case '[t] =>
          val inner = innerFormat.asExprOf[Format[t]]
          '{ ContainerFormats.vectorFormat[t]($inner) }

    def buildMapFormat(valueType: TypeRepr, fieldName: String, ccName: String): Expr[Format[?]] =
      val valueFormat = buildFormatExpr(valueType, fieldName, ccName)
      valueType.asType match
        case '[v] =>
          val inner = valueFormat.asExprOf[Format[v]]
          '{ ContainerFormats.mapFormat[v]($inner) }

    // Check for common Kubernetes types and return built-in format if available
    def buildCommonTypeFormat(fieldType: TypeRepr): Option[Expr[Format[?]]] =
      fieldType.typeSymbol.fullName match
        case "java.time.ZonedDateTime" =>
          Some('{ CommonFormats.zonedDateTimeFormat })
        case "java.time.Instant" =>
          Some('{ CommonFormats.instantFormat })
        case _ =>
          None

    def searchImplicitFormat(fieldType: TypeRepr, fieldName: String, ccName: String): Expr[Format[?]] =
      // Use Implicits.search for better compatibility with Play JSON's implicit resolution
      val formatSearchType = TypeRepr.of[Format].appliedTo(List(fieldType))
      Implicits.search(formatSearchType) match
        case success: ImplicitSearchSuccess =>
          fieldType.asType match
            case '[t] => success.tree.asExprOf[Format[t]]
        case failure: ImplicitSearchFailure =>
          report.errorAndAbort(s"No Format found for ${fieldType.show} in field $ccName.$fieldName: ${failure.explanation}")

    // Generate format val for a case class
    def generateCaseClassFormat(cc: ClassDef): Statement =
      val ccSym = cc.symbol
      val ccTypeRef = ccSym.typeRef
      val ccName = cc.name
      val formatType = TypeRepr.of[OFormat].appliedTo(List(ccTypeRef))

      // Use internal name for non-Spec/Status classes
      val formatName = ccName match
        case "Spec" => "specFormat"
        case "Status" => "statusFormat"
        case other => s"format_$other"

      val flags = ccName match
        case "Spec" | "Status" => Flags.Override | Flags.Protected
        case _ => Flags.Given | Flags.Implicit

      val formatSym = Symbol.newVal(objSym, formatName, formatType, flags, Symbol.noSymbol)
      generatedFormatSymbols = generatedFormatSymbols + (ccSym -> formatSym)

      val fields = ccSym.caseFields
      val fieldNames = fields.map(_.name)
      val fieldNamesExpr: Expr[List[String]] = Expr(fieldNames)

      val fieldFormatExprs: List[Expr[Format[?]]] = fields.map { field =>
        buildFormatExpr(field.info, field.name, ccName)
      }
      val formatListExpr: Expr[List[Format[?]]] = Expr.ofList(fieldFormatExprs)

      // Extract default values for fields
      // In Scala 3, default values are methods on the companion: $lessinit$greater$default$N
      val companion = ccSym.companionModule

      // Build expressions for default values directly (no thunks)
      // Values are evaluated when the format is created, which happens once per format instance
      val defaultExprs: List[Expr[Option[Any]]] = fields.zipWithIndex.map { (field, idx) =>
        val paramIdx = idx + 1
        val defaultMethodName = s"$$lessinit$$greater$$default$$$paramIdx"

        if companion != Symbol.noSymbol then
          companion.methodMember(defaultMethodName).headOption match
            case Some(defaultMethod) =>
              // Get the default value by referencing the companion method
              // The method signature is like: def $lessinit$greater$default$N: T
              val companionTerm = Ref(companion)
              val methodSelect = companionTerm.select(defaultMethod)
              // Pass the value directly - no lambda wrapping
              field.info.widen.asType match
                case '[t] =>
                  val defaultValExpr = methodSelect.asExprOf[t]
                  '{ Some($defaultValExpr: Any) }
            case None =>
              '{ None }
        else
          '{ None }
      }

      val defaultsExpr: Expr[List[Option[Any]]] = Expr.ofList(defaultExprs)

      // Build the format using quotes
      ccTypeRef.asType match
        case '[t] =>
          Expr.summon[Mirror.ProductOf[t]] match
            case Some(mirror) =>
              val formatExpr: Expr[OFormat[t]] = '{
                CrdFormatHelper.createFormat[t]($fieldNamesExpr, $formatListExpr, $defaultsExpr, $mirror)
              }
              ValDef(formatSym, Some(formatExpr.asTerm.changeOwner(formatSym)))

            case None =>
              report.errorAndAbort(s"Cannot find Mirror.ProductOf for ${ccTypeRef.show}")

    // Generate formats for case classes, respecting user overrides
    for cc <- sortedCaseClasses do
      val shouldGenerate = cc.name match
        case "Spec" => !hasUserSpecFormat
        case "Status" => !hasUserStatusFormat && hasStatusClass
        case _ => true  // Always generate for nested types

      if shouldGenerate then
        members += generateCaseClassFormat(cc)

    // ================================================================
    // STEP 3: Generate metadata tuple
    // ================================================================

    val kindExpr = Expr(kind)
    val groupExpr = Expr(group)
    val versionExpr = Expr(version)
    val pluralExpr = Expr(plural)
    val singularExpr = Expr(singular)
    val shortNamesExpr = Expr(shortNames)
    val scopeExpr = Expr(scope)

    val metadataSym = Symbol.newVal(
      objSym,
      "crMetadata",
      TypeRepr.of[(String, String, String, String, String, List[String], String)],
      Flags.Override,
      Symbol.noSymbol
    )

    val metadataExpr = '{ ($kindExpr, $groupExpr, $versionExpr, $pluralExpr, $singularExpr, $shortNamesExpr, $scopeExpr) }
    members += ValDef(metadataSym, Some(metadataExpr.asTerm.changeOwner(metadataSym)))

    members.result()
