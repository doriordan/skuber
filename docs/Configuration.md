## Configuration

While the default configuration of Skuber clients - which assumes the Kubernetes cluster API server is reachable at `http://localhost:8080` - might work well for local development and "kicking the tyres", you will typically need to customise it for other environments.

### Cluster URL

In some simpler cases it might be sufficient to simply override the address used by a client to connect to the cluster API server (or to a [kubectl proxy](https://kubernetes.io/docs/user-guide/kubectl/v1.6/#proxy) for the cluster).

This can be done by setting the environment variable **SKUBER_URL** e.g. 

    export SKUBER_URL=http://kubernetes:8001

### Kubeconfig file

For shared / secured clusters (such as normally occur in production environments), clients should be configured using a [kubeconfig file](https://kubernetes.io/docs/tasks/access-application-cluster/authenticate-across-clusters-kubeconfig/). This is the standard configuration file format used by other Kubernetes clients such as `kubectl`. These files allow the following to be configured:

- Authentication credentials - [see below](#security).
- [Namespace](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/) - this allows the client to read/write Kubernetes resources in different cluster namespaces just by changing runtime configuration, which supports partitioning by team / organization.
- Cluster address - i.e the URL to which the client connects to communicate with the Kubernetes API server. This can be either a non-TLS (http) or TLS (https) URL.

To configure Skuber to use a specific kubeconfig file, set the **SKUBER_CONFIG** environment variable to the location of the config file e.g.

    export SKUBER_CONFIG=file:///home/kubernetes/.kube/config
 
Setting this variable as follows:

    export SKUBER_CONFIG=file

will instruct the client to get its configuration for a Kubeconfig file in the default location (`$HOME/.kube/config`).

The use of the kubeconfig format means that `kubectl` can be used to modify configuration settings for Skuber clients, without requiring direct editing of the configuration file - and this is the recommended approach.

Kubeconfig files can contain multiple contexts, each encapsulating the full details required to configure a client - Skuber always configures itself (when initialised by a `k8sInit` call) from the [current context](http://kubernetes.io/docs/user-guide/kubectl/kubectl_config_use-context/).

Because all of the above configuration items in the configuration file are the same as used by other Kubernetes clients such as kubectl, you (or rather the organization deploying the Skuber application) can share configuration with such other clients or set up separate configuration files for applications depending on organizational security policies, deployment processes and other requirements. 

Note that - unlike the Go language client - Skuber does not attempt to merge different sources of configuration. So if a kubeconfig file is specified via **$SKUBER_CONFIG** then all configuration is sourced from that file.

*(Configuration can alternatively be passed programmatically to the `k8sInit` call, see the programming guide for details.)*

### Security

When using kubeconfig files, Skuber supports standard security configuration as described below.

If the current context specifies a **TLS** connection (i.e. a `https://` URL) to the cluster server, Skuber will utilise the configured **certificate authority** to verify the server (unless the `insecure-skip-tls-verify` flag is set to true, in which case Skuber will trust the server without verification). 

The above cluster configuration details can be set using [this kubectl command](http://kubernetes.io/docs/user-guide/kubectl/kubectl_config_set-cluster/).

For client authentication **client certificates** (cert and private key pairs) can be specified for the case where TLS is in use. In addition to client certificates Skuber will use any **bearer token** or **basic authentication** credentials specified. Token or basic auth can be configured as an alternative to or in conjunction with client certificates. These client credentials can be set using [this kubectl command](http://kubernetes.io/docs/user-guide/kubectl/kubectl_config_set-credentials/).

*(Skuber loads configured server and client certificates / keys directly from the kubeconfig file (or from another location in the file system in the case where the configuration specifies a path rather than embedded data). This means there is no need to store them in the Java trust or key stores.)*


