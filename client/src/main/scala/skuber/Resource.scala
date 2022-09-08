package skuber

/**
 * @author David O'Riordan
 */


import scala.util.matching.Regex

object Resource {
  
  type ResourceList=Map[String, Quantity]
  
  case class Requirements(limits: ResourceList = Map(), requests: ResourceList = Map())
  case class Quota(val kind: String = "ResourceQuota",
      override val apiVersion: String = v1,
      val metadata: ObjectMeta = ObjectMeta(),
      spec: Option[Quota.Spec] = None,
      status: Option[Quota.Status] = None)
    extends ObjectResource 
 
  object Quota {
    val specification: CoreResourceSpecification =CoreResourceSpecification(scope = ResourceSpecification.Scope.Namespaced,
      names = ResourceSpecification.Names(plural="resourcequotas",
        singular="resourcequota",
        kind="ResourceQuota",
        shortNames=List("quota")))
    implicit val rqDef: ResourceDefinition[Quota] = new ResourceDefinition[Resource.Quota] { def spec: ResourceSpecification =specification }
    implicit val rqListDef: ResourceDefinition[ResourceQuotaList] = new ResourceDefinition[ResourceQuotaList] { def spec: ResourceSpecification =specification }

    case class Spec(hard: ResourceList = Map())
    case class Status(hard: ResourceList = Map(), used: ResourceList = Map())
  }  
  
  // standard resource names
  val cpu = "cpu"
  val memory="memory"
  val storage="storage"
  val pods = "pods"
  
  // K8 has a specific format for Resource Quantity type - see
  // https://godoc.org/github.com/GoogleCloudPlatform/kubernetes/pkg/api/resource#Quantity
  
  
  class Quantity(val value: String) {
 
    lazy val amount : BigDecimal = 
      format match {
        case Quantity.DecimalExponent => BigDecimal(new java.math.BigDecimal(value))
        case Quantity.BinarySI => Quantity.parseBinarySI(number, suffix)
        case Quantity.DecimalSI => Quantity.parseDecimalSI(number,suffix)  
    }
      
    val QuantSplitRE: Regex = """^([+-]?[0-9.]+)([eEimkKMGTP]*[-+]?[0-9]*)$""".r
    val ExponentSplitRE: Regex = """^([eE])([-+]?[0-9]*)$""".r
    
    lazy val QuantSplitRE(number, suffix) = value
    
    lazy val format : Quantity.Format = suffix match {
        case "Ki"| "Mi" | "Gi" | "Ti" | "Pi" | "Ei" => Quantity.BinarySI
        case "m" | "" | "k" | "M" | "G" | "T" | "P" | "E" => Quantity.DecimalSI
        case  ExponentSplitRE(_, _) => Quantity.DecimalExponent 
        case _ => throw new Exception("Invalid resource quantity format")
    }
    
    override def toString: Finalizer = value
    override def equals(o: Any) : Boolean = {
      o match {
        case that: Quantity => that.amount.equals(this.amount)
        case _ => false
      }
    }
    override def hashCode: Int = this.amount.hashCode
  }
  
  object Quantity {
    
    import scala.language.implicitConversions
  
    sealed trait Format
    case object BinarySI extends Format
    case object DecimalSI extends Format
    case object DecimalExponent extends Format
   
    val binBe2Suffix = Map((2,10) -> "Ki",
        (2,20) -> "Mi",
        (2,30) -> "Gi",
        (2,40) -> "Ti",
        (2,50) -> "Pi",
        (2,60) -> "Ei",
        (2,0)  -> "")     
        
    val binSuffix2Be: Map[Finalizer, (Int, Int)] = binBe2Suffix map { case (be, suffix) => (suffix, be) }
    
    val decBe2Suffix = Map(  
        (10,-3) -> "m",
        (10,0)  -> "",
        (10,3)  -> "k",
        (10,6)  -> "M",
        (10,9)  -> "G",
        (10,12) -> "T",
        (10,15) -> "P",
        (10,18) -> "E")
    
    val decSuffix2Be: Map[Finalizer, (Int, Int)] = decBe2Suffix map { case (be, suffix) => (suffix, be) }
    
    def parseBinarySI(number: String, suffix: String): BigDecimal = {
      val (base, exponent) = binSuffix2Be(suffix)
      val multiplier = Math.pow(base, exponent).round
      val multPartBigDec = BigDecimal(new java.math.BigDecimal(multiplier))
      val numberPartBigDec = BigDecimal(new java.math.BigDecimal(number))
      numberPartBigDec * multPartBigDec
    }
    
    def parseDecimalSI(number: String, suffix: String): BigDecimal = {
      val (base, exponent) = decSuffix2Be(suffix)
      val num = BigDecimal(new java.math.BigDecimal(number))
      exponent match {
        case 0 => num
        case _ =>
          val mult = BigDecimal(new java.math.BigDecimal(base)).pow(exponent)
          num * mult
      }
    }
     
    def parseDecimalExponent(number: String, suffix: String): Unit = {
      
    }
      
    def apply(quant: String): Quantity = new Quantity(quant)      
  }
}