# Refresh EKS (AWS) Token

[Background](#background) </br>
[Step-by-step guide](#step-by-step-guide) </br>
[Setup the environment variables](#setup-the-environment-variables) </br>
[Create IAM Role](#create-iam-role) </br>
[Create a service account](#create-a-service-account) </br>
[Create the aws-auth mapping](#create-the-aws-auth-mapping) </br>
[Skuber Code example](#skuber-code-example) 

## Background
Skuber has the functionality to refresh your EKS (AWS) token with an IAM role and cluster configurations. 

The initiative:
* Refreshing tokens increasing your k8s cluster security
* Since kubernetes v1.21 service account tokens has an expiration of 1 hour.
  https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-versions.html#kubernetes-1.21


## Step-by-step guide
Pay attention to the fact that skuber can be deployed in one cluster and the cluster you want to control can be another cluster. </br>
In this guide I will use the following: 

`SKUBER_CLUSTER` - the cluster skuber app will be deployed on. </br>
`TARGET_CLUSTER` - the cluster that skuber will be connected to.

### Setup the environment variables
* Make sure aws cli is configured properly

```
export ACCOUNT_ID=$(aws sts get-caller-identity --output text --query Account)
echo $ACCOUNT_ID
```
Set the cluster name and region you need access to (`TARGET_CLUSTER`) </br>
use `aws eks list-clusters` to see the cluster names.

```
export TARGET_CLUSTER=example-cluster
export REGION=us-east-1
```


Set the cluster name which skuber app will run from (`SKUBER_CLUSTER`)

Set the namespace name which skuber app will run from

Set the oidc provider id

Set the service account name that skuber app will be attached to (we will create it later)

```
export SKUBER_CLUSTER=skuber-cluster
export SKUBER_NAMESPACE=skuber-namespace
export OIDC=$(aws eks describe-cluster --name $SKUBER_CLUSTER --output text --query cluster.identity.oidc.issuer | cut -d'/' -f3,4,5)
echo $OIDC
export SKUBER_SA=skuber-serviceaccount
```

### Create IAM Role 

This role will map the service account that skuber uses to the cluster it connects to. </br>
You can add more clusters under "Resource"
* Note: eks actions probably need to be minimized and not set to "eks:*" . </br>
```
cat > skuber_iam_role.json  <<EOL
{
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": "eks:*",
        "Resource": [
          "arn:aws:eks:*:${ACCOUNT_ID}:cluster/${TARGET_CLUSTER}"
        ]
      },
      {
          "Sid": "",
          "Effect": "Allow",
          "Principal": {
              "Federated": "arn:aws:iam::${ACCOUNT_ID}:oidc-provider/${OIDC}"
          },
          "Action": "sts:AssumeRoleWithWebIdentity",
          "Condition": {
              "StringLike": {
                  "${OIDC}:sub": "system:serviceaccount:${SKUBER_NAMESPACE}:${SKUBER_SA}"
              }
          }
      }
    ]
}
EOL
```

Create and set IAM role name
```
export IAM_ROLE_NAME=skuber-eks
aws iam create-role \
--role-name $IAM_ROLE_NAME \
--description "Kubernetes role for skuber client" \
--assume-role-policy-document file://skuber_iam_role.json \
--output text \
--query 'Role.Arn'
```

### Create a service account
Change the context to `SKUBER_CLUSTER` and create the service account </br>

```
kubectl config use-context arn:aws:eks:${REGION}:${ACCOUNT_ID}:cluster/${SKUBER_CLUSTER}

kubectl apply -n $SKUBER_NAMESPACE -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
name: ${SKUBER_SA}
EOF
```

### Create the aws-auth mapping
In order to map aws iam role to the actual kubernetes cluster permissions, we need to create a mapping:
IAM Role -> Kubernetes Permissions

For this example I'm using existing masters permissions group, you can create something more specific with [RBAC](https://docs.aws.amazon.com/eks/latest/userguide/add-user-role.html).
* You need to create this mapping on every cluster that you want skuber will be able to interact with.

Change the context to `TARGET_CLUSTER`.
```
kubectl config use-context arn:aws:eks:${REGION}:${ACCOUNT_ID}:cluster/${TARGET_CLUSTER}
kubectl edit configmap aws-auth -n kube-system
```

Add the following mapping
* Replace the variables with the actual values
```
    - rolearn: arn:aws:iam::$ACCOUNT_ID:role/$IAM_ROLE_NAME
      username: ci
      groups:
        - system:masters
```


### Skuber Code example
A working example for using `AwsAuthRefreshable`
```
implicit private val as = ActorSystem()
  implicit private val ex = as.dispatcher
  val namespace = System.getenv("namespace")
  val serverUrl = System.getenv("serverUrl")
  val certificate = Base64.getDecoder.decode(System.getenv("certificate"))
  val clusterName = System.getenv("clusterName")
  val region = Regions.fromName(System.getenv("region"))
  val cluster = Cluster(server = serverUrl, certificateAuthority = Some(Right(certificate)), clusterName = Some(clusterName), awsRegion = Some(region))

  val context = Context(cluster = cluster, authInfo = AwsAuthRefreshable(cluster = Some(cluster)))

  val k8sConfig = Configuration(clusters = Map(clusterName -> cluster), contexts = Map(clusterName -> context)).useContext(context)

  val k8s: KubernetesClient = k8sInit(k8sConfig)
  listPods(namespace, 0)
  listPods(namespace, 5)
  listPods(namespace, 11)

  k8s.close
  Await.result(as.terminate(), 10.seconds)
  System.exit(0)

  def listPods(namespace: String, minutesSleep: Int): Unit = {
    println(s"Sleeping $minutesSleep minutes...")
    Thread.sleep(minutesSleep * 60 * 1000)
    println(DateTime.now)
    val pods = Await.result(k8s.listInNamespace[PodList](namespace), 10.seconds)
    println(pods.items.map(_.name))
  }
```
