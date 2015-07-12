package skuber.model

import org.specs2.mutable.Specification // for unit-style testing

/**
 * @author David O'Riordan
 */
class ModelSpec extends Specification {
  "This is a unit specification for the skuber data model. ".txt
  
  // Resource type
  "A resource quantity can be constructed from decimal SI values\n" >> {
    "where a value of 100m equates to 0.1" >> { Resource.Quantity("100m").amount == 0.1 }
    "where a value of 100k equates to 100000" >> { Resource.Quantity("100k").amount == 100000 }
    "where a value of 100M equates to 100000000" >> { Resource.Quantity("100M").amount == 100000000 }
    "where a value of 100G equates to 100E+9" >> { Resource.Quantity("100G").amount == 100E+9 }
    "where a value of 100T equates to 100E+12" >> { Resource.Quantity("100T").amount == 100E+12 }
     
  }
    
}