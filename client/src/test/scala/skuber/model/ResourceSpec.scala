package skuber.model

import org.specs2.mutable.Specification


/**
 * @author David O'Riordan
 */
class ResourceSpec extends Specification {
  "This is a unit specification for the skuber Resource model. ".txt
  
  // Resource type
  "A resource quantity can be constructed from decimal SI values\n" >> {
    "where a value of 100m equates to 0.1" >> { Resource.Quantity("100m").amount mustEqual 0.1 }
    "where a value of 100k equates to 100000" >> { Resource.Quantity("100k").amount mustEqual 100000 }
    "where a value of 100M equates to 100000000" >> { Resource.Quantity("100M").amount mustEqual 100000000 }
    "where a value of 100G equates to 100E+9" >> { Resource.Quantity("100G").amount mustEqual 100E+9 }
    "where a value of 100T equates to 100E+12" >> { Resource.Quantity("100T").amount mustEqual 100E+12 }
    "where a value of 100P equates to 100E+15" >> { Resource.Quantity("100P").amount mustEqual 100E+15 }
    "where a value of 100E equates to 100E+18" >> { Resource.Quantity("100E").amount mustEqual 100E+18 }
  }
  
  "A resource quantity can be constructed from values with scientific E notation\n" >> {
    "where a value of 0.01E+5 equates to 1000" >> { Resource.Quantity("0.01E+5").amount mustEqual 1000 }
    "where a value of 10010.56E-3 equates to 10.01056" >> { Resource.Quantity("10010.56E-3").amount mustEqual 10.01056 }
    "where a value of 55.67e+6 equates to 55670000" >> { Resource.Quantity("55.67e+6").amount mustEqual 55670000 }
    "where a value of 5e+3 equates to 5000" >> { Resource.Quantity("5e+3").amount mustEqual 5000 }
    "where a value of 67700e-33 equates to 67.700" >> { Resource.Quantity("67700e-3").amount mustEqual 67.700 }
  }
  
  "A resource quantity can be constructed from binary SI values\n" >> {
    "where a value of 100Ki equates to 102400" >> { Resource.Quantity("100Ki").amount mustEqual 102400 }
    "where a value of 10Mi equates to 10485760" >> { Resource.Quantity("10Mi").amount mustEqual 10485760 }    
    "where a value of 10Ti equates to 10 *(2 ^ 40) " >> { Resource.Quantity("10Ti").amount mustEqual 10 * Math.pow(2,40) }
    "where a value of 10Pi equates to 10 *(2 ^ 50) " >> { Resource.Quantity("10Pi").amount mustEqual 10 * Math.pow(2,50) }
    "where a value of 10Ei equates to 10 *(2 ^ 60) " >> { 
      val mult = new java.math.BigInteger("10") 
      val base = new java.math.BigInteger("2")
      val value = base.pow(60).multiply(mult)
      val decValue = scala.math.BigDecimal(new java.math.BigDecimal(value))
      System.err.println("....10Ei = " + decValue)
      Resource.Quantity("10Ei").amount mustEqual decValue
    }    
  }
  
  "A resource quantity can be constructed for plain integer and decimal values with no suffixes\n" >> {
    "where a value of 10 is valid" >> { Resource.Quantity("10").amount mustEqual 10 }
    "where a value of -10 is valid" >> { Resource.Quantity("-10").amount mustEqual -10 }
    "where a value of 10.55 is valid" >> { Resource.Quantity("10.55").amount mustEqual 10.55 }
    "where a value of -10.55 is valid" >> { Resource.Quantity("-10.55").amount mustEqual -10.55 }
  }
  
  "A resource quantity will reject bad values\n" >> {
    "where constructing from a value of 10Zi results in an exception" >> 
      { 
        def badVal = Resource.Quantity("10Zi").amount 
        badVal must throwAn[Exception] 
      }
  }
    
}