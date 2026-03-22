package skuber.internal

import javax.net.ssl.SSLContext
import skuber.api.client.Context
import skuber.api.security.TLS

object TlsHelper {
  def buildSSLContext(k8sContext: Context): Option[SSLContext] =
    TLS.establishSSLContext(k8sContext)
}
