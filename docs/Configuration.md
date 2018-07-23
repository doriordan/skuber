## Configuration

Skuber supports both out-of-cluster and in-cluster configurations.
Сonfiguration algorithm can be described as follows:

Initailly Skuber tries out-of-cluster methods in sequence (stops on first successful):
 1. Read `SKUBER_URL` environment variable and use it as kubectl proxy url. If not set then:
 1. Read `SKUBER_CONFIG` environment variable and if is equal to:
    * `file`  - Skuber will read `~/.kube/config` and use it as configuration source
    * `proxy` - Skuber will assume that kubectl proxy running on `localhost:8080` and will use it as endpoint to cluster API 
    * Otherwise treats contents of `SKUBER_CONFIG` as a file path to kubeconfig file (use it if your kube config file is in custom location). If not present then:
 1. Read `KUBECONFIG` environment variable and use its contents as path to kubconfig file (similar to `SKUBER_CONFIG`)


If all above fails Skuber tries [in-cluster configuration method](https://kubernetes.io/docs/tasks/access-application-cluster/access-cluster/#accessing-the-api-from-a-pod)


### Cluster URL

If proxying via a [kubectl proxy](https://kubernetes.io/docs/user-guide/kubectl/v1.6/#proxy) then you can configure Skuber to connect through that proxy by setting the SKUBER_URL environment variable to point at it e.g.

    export SKUBER_URL=http://locahost:8001

If the cluster URL is set this way, then the SKUBER_CONFIG and KUBECONFIG environment variables below are ignored by Skuber.

Alternatively, you can programmatically configure the proxy details by using one of the [Configuration helper methods](https://github.com/doriordan/skuber/blob/master/client/src/main/scala/skuber/api/Configuration.scala#L32) to construct an appropriate Configuration object to be passed to the `k8sInit` call.

### Kubeconfig file

If not using a `kubectl proxy` then most clients will be configured using a [kubeconfig file](https://kubernetes.io/docs/tasks/access-application-cluster/authenticate-across-clusters-kubeconfig/). This is the standard configuration file format used by other Kubernetes clients such as `kubectl`. These files allow the following to be configured:

- Authentication credentials - [see below](#security).
- [Namespace](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/) - this allows the client to read/write Kubernetes resources in different cluster namespaces just by changing runtime configuration, which supports partitioning by team / organization.
- Cluster address - i.e the URL to which the client connects to communicate with the Kubernetes API server. This can be either a non-TLS (http) or TLS (https) URL.

To configure Skuber to use a specific kubeconfig file, set the `SKUBER_CONFIG` or`KUBECONFIG` environment variable to the location of the config file e.g.

    export SKUBER_CONFIG=file:///home/kubernetes/.kube/config
 
Setting this variable as follows:

    export SKUBER_CONFIG=file

will instruct the client to get its configuration for a Kubeconfig file in the default location (`$HOME/.kube/config`), assuming the application uses the default`k8sInit()` method to initialise the Skuber client.

If SKUBER_CONFIG environment variable is not set then the fallback is to get the kubeconfig location from the standard Kubernetes / kubectl KUBECONFIG variable.

If none of these environment variables are set then the kubeconfig file is loaded from its default location.

The use of the kubeconfig format means that `kubectl` can be used to modify configuration settings for Skuber clients, without requiring direct editing of the configuration file - and this is the recommended approach.

Kubeconfig files can contain multiple contexts, each encapsulating the full details required to configure a client - Skuber always configures itself (when initialised by a `k8sInit` call) from the [current context](https://kubernetes.io/docs/user-guide/kubectl/v1.6/#-em-current-context-em-).

Because all of the above configuration items in the configuration file are the same as used by other Kubernetes clients such as kubectl, you (or rather the organization deploying the Skuber application) can share configuration with such other clients or set up separate configuration files for applications depending on organizational security policies, deployment processes and other requirements. 

Note that - unlike the Go language client - Skuber does not attempt to merge different sources of configuration. So if a kubeconfig file is specified via **$SKUBER_CONFIG** then all configuration is sourced from that file.

*(Configuration can alternatively be passed programmatically to the `k8sInit` call, see the programming guide for details.)*

### Security

When using kubeconfig files, Skuber supports standard security configuration as described below.

If the current context specifies a **TLS** connection (i.e. a `https://` URL) to the cluster server, Skuber will utilise the configured **certificate authority** to verify the server (unless the `insecure-skip-tls-verify` flag is set to true, in which case Skuber will trust the server without verification). 

The above cluster configuration details can be set using [this kubectl command](https://kubernetes.io/docs/user-guide/kubectl/v1.6/#-em-set-cluster-em-).

For client authentication **client certificates** (cert and private key pairs) can be specified for the case where TLS is in use. In addition to client certificates Skuber will use any **bearer token** or **basic authentication** credentials specified. Token or basic auth can be configured as an alternative to or in conjunction with client certificates. These client credentials can be set using [this kubectl command](https://kubernetes.io/docs/user-guide/kubectl/v1.6/#-em-set-credentials-em-).

*(Skuber loads configured server and client certificates / keys directly from the kubeconfig file (or from another location in the file system in the case where the configuration specifies a path rather than embedded data). This means there is no need to store them in the Java trust or key stores.)*


