package skuber.zio.internal

import zio.*
import zio.http.{Client, ClientSSLCertConfig, ClientSSLConfig, DnsResolver, ZClient}
import zio.http.netty.NettyConfig
import skuber.api.client.{AuthInfo, CertAuth, Cluster, Context, PathOrData}

import java.io.{File, FileOutputStream}

/** Builds a zio-http [[Client]] with TLS settings derived from a kubeconfig [[Context]].
 *
 *  For clusters that use TLS (https), the client is configured with:
 *  - A trust manager derived from the cluster's certificate-authority (server cert verification)
 *  - A key manager derived from client-certificate / client-key for [[CertAuth]] clusters
 *
 *  When cert or key data is embedded inline in the kubeconfig (base64-decoded PEM bytes),
 *  temp files are written to satisfy zio-http's file-path based SSL API, and deleted on scope
 *  finalisation.
 */
private[zio] object ZioTlsHelper:

  private val HttpPattern = "http:.*".r

  /** Build a [[Client]] scoped to the caller's [[Scope]], configured for the given context. */
  def buildClient(config: skuber.api.Configuration): ZIO[Scope, Throwable, Client] =
    for
      sslConfigOpt <- buildSSLConfig(config.currentContext)
      clientConfig  = sslConfigOpt.fold(ZClient.Config.default)(ssl => ZClient.Config.default.ssl(ssl))
      clientLayer   = (
                        ZLayer.succeed(clientConfig) ++
                        ZLayer.succeed(NettyConfig.defaultWithFastShutdown) ++
                        DnsResolver.default
                      ) >>> ZClient.live
      client       <- clientLayer.build.map(_.get[Client])
    yield client

  private def buildSSLConfig(context: Context): ZIO[Scope, Throwable, Option[ClientSSLConfig]] =
    context.cluster.server match
      case HttpPattern(_*) => ZIO.succeed(None)
      case _ =>
        if context.cluster.insecureSkipTLSVerify then
          ZIO.succeed(Some(ClientSSLConfig.Default))
        else
          for
            serverConfig <- buildServerConfig(context.cluster)
            fullConfig   <- context.authInfo match
                              case CertAuth(clientCert, clientKey, _) =>
                                buildClientAndServerConfig(serverConfig, clientCert, clientKey)
                              case _ =>
                                ZIO.succeed(serverConfig)
          yield Some(fullConfig)

  /** Trust manager config from the cluster CA cert. */
  private def buildServerConfig(cluster: Cluster): ZIO[Scope, Throwable, ClientSSLConfig] =
    cluster.certificateAuthority match
      case None                => ZIO.succeed(ClientSSLConfig.Default)
      case Some(Left(path))    => ZIO.succeed(ClientSSLConfig.FromCertFile(path))
      case Some(Right(data))   => writeTempFile(data, "ca", ".crt").map(ClientSSLConfig.FromCertFile(_))

  /** Combine trust manager (server CA) + key manager (client cert/key) configs. */
  private def buildClientAndServerConfig(
    serverConfig: ClientSSLConfig,
    clientCert: PathOrData,
    clientKey: PathOrData
  ): ZIO[Scope, Throwable, ClientSSLConfig] =
    for
      certPath <- pathToString(clientCert, "client-cert", ".crt")
      keyPath  <- pathToString(clientKey,  "client-key",  ".key")
    yield ClientSSLConfig.FromClientAndServerCert(
      serverConfig,
      ClientSSLCertConfig.FromClientCertFile(certPath, keyPath)
    )

  private def pathToString(pad: PathOrData, prefix: String, suffix: String): ZIO[Scope, Throwable, String] =
    pad match
      case Left(path)  => ZIO.succeed(path)
      case Right(data) => writeTempFile(data, prefix, suffix)

  /** Write PEM bytes to a temp file, deleting it when the scope closes. */
  private def writeTempFile(data: Array[Byte], prefix: String, suffix: String): ZIO[Scope, Throwable, String] =
    ZIO.acquireRelease(
      ZIO.attempt {
        val file = File.createTempFile(s"skuber-$prefix-", suffix)
        val fos  = new FileOutputStream(file)
        try fos.write(data)
        finally fos.close()
        file.getAbsolutePath
      }
    )(path => ZIO.attempt(new File(path).delete()).ignoreLogged)
