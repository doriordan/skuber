package skuber

/**
  * @author David O'Riordan
  */
package object networking {
  type NetworkPolicyList = ListResource[NetworkPolicy]
  type IngressList = ListResource[Ingress]
}
