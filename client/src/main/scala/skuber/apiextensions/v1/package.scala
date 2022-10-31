package skuber.apiextensions

import skuber.ListResource

/**
  * @author David O'Riordan
  */
package object v1 {
  type CustomResourceDefinitionList = ListResource[CustomResourceDefinition]
}
