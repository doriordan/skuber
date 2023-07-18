package skuber.model.apiextensions

import skuber.model.ListResource

/**
  * @author David O'Riordan
  */
package object v1 {
  type CustomResourceDefinitionList = ListResource[CustomResourceDefinition]
}
