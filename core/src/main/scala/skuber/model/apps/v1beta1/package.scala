package skuber.model.apps

import skuber.model.ListResource

package object v1beta1 {
    val appsAPIVersion = "apps/v1beta1"

    type DeploymentList = ListResource[skuber.model.apps.v1beta1.Deployment]
    type StatefulSetList = ListResource[skuber.model.apps.v1beta1.StatefulSet]

}
