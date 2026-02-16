package skuber.catseffect.internal

import javax.net.ssl.SSLContext
import skuber.api.client.Context
import skuber.api.security.TLS

/** Builds an SSLContext from a skuber Context, delegating to the core TLS utilities. */
private[catseffect] object TlsHelper:

  /**
   * Build an SSLContext from the given Kubernetes API context.
   *
   * Returns Some(sslContext) for https clusters, None for plain http.
   * Handles:
   *  - InsecureSkipTLSVerify (trust-all TrustManager)
   *  - CA certificate from cluster.certificateAuthority (path or inline data)
   *  - Client cert/key from CertAuth (path or inline data)
   */
  def buildSSLContext(k8sContext: Context): Option[SSLContext] =
    TLS.establishSSLContext(k8sContext)
