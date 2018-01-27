package skuber.apps

import skuber.ListResource

package object v1beta1 {
    val appsAPIVersion = "apps/v1beta1"

    type StatefulSetList = ListResource[skuber.apps.v1beta1.StatefulSet]

}
