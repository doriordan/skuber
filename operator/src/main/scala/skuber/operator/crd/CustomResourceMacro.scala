package skuber.operator.crd

import scala.annotation.experimental
import scala.quoted.*

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

        // Check which formatters the user has already overridden in the object body so we don't generate them
        val hasUserSpecFormat = body.exists {
          case vd: ValDef => vd.name == "specFormat"
          case _ => false
        }
        val hasUserStatusFormat = body.exists {
          case vd: ValDef => vd.name == "statusFormat"
          case _ => false
        }

        val newMembers = generateMembers(
          objSym, kind, group, version, pluralName, singularName, shortNames, scopeValue,
          hasStatusClass, statusSubresource, scaleSubresource,
          hasUserSpecFormat, hasUserStatusFormat
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
    hasUserStatusFormat: Boolean
  ): List[quotes.reflect.Statement] =
    import quotes.reflect.*

    val members = List.newBuilder[Statement]

    val specTypeSym = objSym.typeMember("Spec")
    val specTypeRef = specTypeSym.typeRef

    // 1. Generate protected val specFormat unless user provided their own
    if !hasUserSpecFormat then
      members += generateFormatVal(objSym, "specFormat", specTypeSym, specTypeRef)

    // 2. Generate protected val statusFormat if has status class and user didn't provide their own
    if hasStatusClass && !hasUserStatusFormat then
      val statusTypeSym = objSym.typeMember("Status")
      val statusTypeRef = statusTypeSym.typeRef
      members += generateFormatVal(objSym, "statusFormat", statusTypeSym, statusTypeRef)

    // 3. Generate metadata tuple
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

  /**
   * Generate: protected val <memberName>: OFormat[T] = FormatHelper.deriveFormat[T](fieldNames)(using mirror)
   *
   * Uses FormatHelper.deriveFormat which leverages Mirror.ProductOf for
   * type-safe construction, avoiding lambda generation in the macro output.
   *
   * The generated val is protected and overrides the abstract val in the trait.
   * The trait's concrete given delegates to this val, making the format available
   * through TASTy for other compilation units.
   */
  private def generateFormatVal(using Quotes)(
    objSym: quotes.reflect.Symbol,
    memberName: String,
    typeSym: quotes.reflect.Symbol,
    typeRef: quotes.reflect.TypeRepr
  ): quotes.reflect.Statement =
    import quotes.reflect.*

    val oformatType = TypeRepr.of[play.api.libs.json.OFormat].appliedTo(List(typeRef))
    val sym = Symbol.newVal(
      objSym,
      memberName,
      oformatType,
      Flags.Override | Flags.Protected,
      Symbol.noSymbol
    )

    val fields = typeSym.caseFields
    val fieldNames = fields.map(_.name)
    val fieldNamesExpr = Expr(fieldNames)

    // Search for Mirror.ProductOf[T] to pass explicitly
    val mirrorType = TypeRepr.of[scala.deriving.Mirror.ProductOf].appliedTo(List(typeRef))
    val mirrorSearch = Implicits.search(mirrorType)

    val mirrorTree = mirrorSearch match
      case result: ImplicitSearchSuccess => result.tree
      case failure: ImplicitSearchFailure =>
        report.errorAndAbort(
          s"Cannot find Mirror.ProductOf for ${typeRef.show}. " +
          s"Ensure ${typeSym.name} is a case class. Details: ${failure.explanation}"
        )

    // Build: FormatHelper.deriveFormat[T](fieldNames)(using mirror)
    val formatHelperSym = Symbol.requiredModule("skuber.operator.crd.FormatHelper")
    val deriveFormatMethod = formatHelperSym.methodMember("deriveFormat").head

    val call = Apply(
      Apply(
        TypeApply(
          Select(Ref(formatHelperSym), deriveFormatMethod),
          List(Inferred(typeRef))
        ),
        List(fieldNamesExpr.asTerm)
      ),
      List(mirrorTree)
    )

    ValDef(sym, Some(call.changeOwner(sym)))
